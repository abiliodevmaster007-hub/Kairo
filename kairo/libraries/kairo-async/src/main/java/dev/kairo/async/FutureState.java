package dev.kairo.async;

import dev.kairo.lifecycle.Disposable;
import dev.kairo.lifecycle.LifecycleScope;
import dev.kairo.state.Derived;
import dev.kairo.state.State;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Limited cooperative async state bound to a LifecycleScope.
 * Guarantees generation-guarded updates (no stale overwrite on reload),
 * cooperative cancellation on unmount/destroy, and glitch-free State transactions.
 */
public final class FutureState<T> implements Disposable {
    public enum Status { IDLE, RUNNING, SUCCESS, ERROR, CANCELLED }

    public interface Scheduler {
        void execute(Runnable task);
    }

    @FunctionalInterface
    public interface CancellableWork<T> {
        T call(CancellationToken token) throws Exception;
    }

    public static final class Schedulers {
        public static final Scheduler IO = KairoSchedulers.io();
        public static final Scheduler COMPUTE = KairoSchedulers.compute();
        public static final Scheduler IMMEDIATE = KairoSchedulers.immediate();

        private Schedulers() {}

        public static Scheduler ui() {
            return KairoSchedulers.ui();
        }
    }

    public final State<Status> status = State.of(Status.IDLE);
    public final State<T> value = State.of(null);
    public final State<Throwable> error = State.of(null);

    public final Derived<Boolean> isIdle = Derived.derive(() -> status.get() == Status.IDLE);
    public final Derived<Boolean> isLoading = Derived.derive(() -> status.get() == Status.RUNNING);
    public final Derived<Boolean> isSuccess = Derived.derive(() -> status.get() == Status.SUCCESS);
    public final Derived<Boolean> isError = Derived.derive(() -> status.get() == Status.ERROR);
    public final Derived<Boolean> isCancelled = Derived.derive(() -> status.get() == Status.CANCELLED);
    public final Derived<Boolean> isDone = Derived.derive(() -> {
        Status s = status.get();
        return s == Status.SUCCESS || s == Status.ERROR || s == Status.CANCELLED;
    });

    private final LifecycleScope scope;
    private final Scheduler workerScheduler;
    private final Scheduler uiScheduler;
    private final AtomicLong activeGeneration = new AtomicLong(0);
    private final AtomicReference<CancellationToken> currentToken = new AtomicReference<>(CancellationToken.create());
    private final AtomicBoolean registeredForMount = new AtomicBoolean(false);
    private volatile CancellableWork<T> lastWork;

    private final List<Consumer<T>> successListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> cancelListeners = new CopyOnWriteArrayList<>();

    public FutureState() {
        this(null, Schedulers.IO, KairoSchedulers.ui());
    }

    public FutureState(LifecycleScope scope, Scheduler workerScheduler, Scheduler uiScheduler) {
        this.scope = scope;
        this.workerScheduler = Objects.requireNonNull(workerScheduler, "workerScheduler cannot be null");
        this.uiScheduler = Objects.requireNonNull(uiScheduler, "uiScheduler cannot be null");
        ensureBoundToMount();
    }

    private void ensureBoundToMount() {
        if (scope != null && !scope.isDestroyed() && registeredForMount.compareAndSet(false, true)) {
            scope.ownForMount(() -> {
                registeredForMount.set(false);
                cancel();
            });
        }
    }

    public static <T> FutureState<T> create(
            LifecycleScope scope,
            Scheduler workerScheduler,
            Scheduler uiScheduler) {
        return new FutureState<>(scope, workerScheduler, uiScheduler);
    }

    public static <T> FutureState<T> run(
            LifecycleScope scope,
            Scheduler workerScheduler,
            Scheduler uiScheduler,
            Callable<T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        return run(scope, workerScheduler, uiScheduler, token -> {
            token.throwIfCancelled();
            T result = work.call();
            token.throwIfCancelled();
            return result;
        });
    }

    public static <T> FutureState<T> run(
            LifecycleScope scope,
            Scheduler workerScheduler,
            Scheduler uiScheduler,
            CancellableWork<T> work) {
        FutureState<T> future = new FutureState<>(scope, workerScheduler, uiScheduler);
        future.launch(work);
        return future;
    }

    public static <T> FutureState<T> run(LifecycleScope scope, CancellableWork<T> work) {
        return run(scope, Schedulers.IO, KairoSchedulers.ui(), work);
    }

    public FutureState<T> launch(Callable<T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        return launch(token -> {
            token.throwIfCancelled();
            T result = work.call();
            token.throwIfCancelled();
            return result;
        });
    }

    public FutureState<T> launch(CancellableWork<T> work) {
        Objects.requireNonNull(work, "work cannot be null");
        this.lastWork = work;

        long generation = activeGeneration.incrementAndGet();
        CancellationToken previous = currentToken.get();
        if (previous != null) {
            previous.cancel();
        }

        if (scope != null && scope.isDestroyed()) {
            CancellationToken deadToken = CancellationToken.create();
            deadToken.cancel();
            currentToken.set(deadToken);
            markCancelled();
            return this;
        }
        ensureBoundToMount();

        CancellationToken token = CancellationToken.create();
        currentToken.set(token);

        State.transaction(() -> {
            error.set(null);
            status.set(Status.RUNNING);
        });

        workerScheduler.execute(() -> {
            if (isStaleOrCancelled(generation, token)) {
                return;
            }
            try {
                T result = work.call(token);
                if (isStaleOrCancelled(generation, token)) {
                    return;
                }
                uiScheduler.execute(() -> {
                    if (isStaleOrCancelled(generation, token)) {
                        return;
                    }
                    State.transaction(() -> {
                        value.set(result);
                        error.set(null);
                        status.set(Status.SUCCESS);
                    });
                    for (Consumer<T> listener : successListeners) {
                        listener.accept(result);
                    }
                });
            } catch (CancellationToken.AsyncCancelledException cancelledEx) {
                uiScheduler.execute(() -> {
                    if (generation == activeGeneration.get()) {
                        markCancelled();
                    }
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                if (isStaleOrCancelled(generation, token)) {
                    return;
                }
                dispatchFailure(generation, token, interrupted);
            } catch (Throwable t) {
                if (isStaleOrCancelled(generation, token)) {
                    return;
                }
                dispatchFailure(generation, token, t);
            }
        });
        return this;
    }

    public FutureState<T> reload() {
        if (lastWork != null) {
            launch(lastWork);
        }
        return this;
    }

    public FutureState<T> retry() {
        if (status.get() != Status.RUNNING && lastWork != null) {
            launch(lastWork);
        }
        return this;
    }

    public FutureState<T> onSuccess(Consumer<T> handler) {
        if (handler == null) return this;
        successListeners.add(handler);
        if (status.get() == Status.SUCCESS) {
            handler.accept(value.get());
        }
        return this;
    }

    public FutureState<T> onError(Consumer<Throwable> handler) {
        if (handler == null) return this;
        errorListeners.add(handler);
        if (status.get() == Status.ERROR) {
            handler.accept(error.get());
        }
        return this;
    }

    public FutureState<T> onCancel(Runnable handler) {
        if (handler == null) return this;
        cancelListeners.add(handler);
        if (status.get() == Status.CANCELLED) {
            handler.run();
        }
        return this;
    }

    public CancellationToken currentToken() {
        return currentToken.get();
    }

    public T valueOr(T fallback) {
        return status.get() == Status.SUCCESS && value.get() != null ? value.get() : fallback;
    }

    public AsyncResult<T> snapshot() {
        return switch (status.get()) {
            case IDLE -> new AsyncResult.Idle<>();
            case RUNNING -> new AsyncResult.Loading<>();
            case SUCCESS -> new AsyncResult.Success<>(value.get());
            case ERROR -> new AsyncResult.Failure<>(error.get());
            case CANCELLED -> new AsyncResult.Cancelled<>();
        };
    }

    public void cancel() {
        activeGeneration.incrementAndGet();
        CancellationToken token = currentToken.get();
        boolean newlyCancelled = token != null && token.cancel();
        Status current = status.get();
        if (current == Status.RUNNING || current == Status.IDLE || newlyCancelled) {
            markCancelled();
        }
    }

    private void dispatchFailure(long generation, CancellationToken token, Throwable failure) {
        uiScheduler.execute(() -> {
            if (isStaleOrCancelled(generation, token)) {
                return;
            }
            State.transaction(() -> {
                error.set(failure);
                status.set(Status.ERROR);
            });
            for (Consumer<Throwable> listener : errorListeners) {
                listener.accept(failure);
            }
        });
    }

    private void markCancelled() {
        Status current = status.get();
        if (current == Status.SUCCESS || current == Status.ERROR) {
            return;
        }
        boolean notify = current != Status.CANCELLED;
        status.set(Status.CANCELLED);
        if (notify) {
            for (Runnable listener : cancelListeners) {
                listener.run();
            }
        }
    }

    private boolean isStaleOrCancelled(long generation, CancellationToken token) {
        return generation != activeGeneration.get()
                || token.isCancelled()
                || (scope != null && scope.isDestroyed());
    }

    @Override
    public void dispose() {
        cancel();
        isIdle.dispose();
        isLoading.dispose();
        isSuccess.dispose();
        isError.dispose();
        isCancelled.dispose();
        isDone.dispose();
        successListeners.clear();
        errorListeners.clear();
        cancelListeners.clear();
    }
}
