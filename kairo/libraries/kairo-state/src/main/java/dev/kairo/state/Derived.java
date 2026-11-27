package dev.kairo.state;

import dev.kairo.lifecycle.Disposable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Derived reactive computation supporting nested Derived/ObservableList tracking,
 * cyclic dependency detection, and coalesced transaction flushes.
 */
public final class Derived<T> implements Disposable, ReactiveSource {
    private static final ThreadLocal<Set<Derived<?>>> ACTIVE_COMPUTATIONS =
            ThreadLocal.withInitial(LinkedHashSet::new);

    private final Supplier<T> computer;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Derived<?>> derivedDependents = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();
    private final List<Disposable> depSubscriptions = new ArrayList<>();
    private volatile T cachedValue;
    private volatile boolean initialized;
    private volatile boolean disposed;

    public Derived(Supplier<T> computer) {
        this.computer = computer;
        recompute(false);
    }

    public static <T> Derived<T> derive(Supplier<T> computer) {
        return new Derived<>(computer);
    }

    public T get() {
        State.recordRead(this);
        return cachedValue;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public Disposable subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Disposable subscribeAny(Runnable listener) {
        return subscribe(ignored -> listener.run());
    }

    Disposable subscribeDerived(Derived<?> dependent) {
        if (!derivedDependents.contains(dependent)) {
            derivedDependents.add(dependent);
        }
        return () -> derivedDependents.remove(dependent);
    }

    void onDependencyChanged() {
        State.TransactionBuffer tx = State.currentTransaction();
        if (tx != null) {
            tx.scheduleDerived(this);
            return;
        }
        recompute(true);
    }

    void flushInTransaction(State.TransactionBuffer tx) {
        boolean changed = recomputeInternal();
        if (!changed) return;
        for (Derived<?> child : derivedDependents) {
            tx.scheduleDerived(child);
        }
        T snapshot = this.cachedValue;
        for (Consumer<T> listener : listeners) {
            tx.scheduleCallback(this, listener, () -> listener.accept(snapshot));
        }
    }

    private void recompute(boolean notify) {
        boolean changed = recomputeInternal();
        if (notify && changed) {
            for (Derived<?> child : derivedDependents) {
                child.onDependencyChanged();
            }
            T snapshot = this.cachedValue;
            for (Consumer<T> listener : listeners) {
                try {
                    listener.accept(snapshot);
                } catch (RuntimeException ex) {
                    ReactiveRuntimeConfig.reportListenerError("Derived.listener", ex);
                }
            }
        }
    }

    private boolean recomputeInternal() {
        synchronized (lock) {
            if (disposed) return false;
            Set<Derived<?>> active = ACTIVE_COMPUTATIONS.get();
            if (!active.add(this)) {
                throw new IllegalStateException("Cyclic Derived dependency detected");
            }
            try {
                disposeDepSubscriptions();
                State.beginTracking();
                T nextValue;
                Set<ReactiveSource> deps;
                try {
                    nextValue = computer.get();
                } finally {
                    deps = State.endTracking();
                }

                for (ReactiveSource dep : deps) {
                    if (dep instanceof State<?> st) {
                        depSubscriptions.add(st.subscribeDerived(this));
                    } else if (dep instanceof Derived<?> dv && dv != this) {
                        depSubscriptions.add(dv.subscribeDerived(this));
                    } else if (dep != this) {
                        depSubscriptions.add(dep.subscribeAny(this::onDependencyChanged));
                    }
                }

                boolean changed = !initialized || !Objects.equals(cachedValue, nextValue);
                cachedValue = nextValue;
                initialized = true;
                return changed;
            } finally {
                active.remove(this);
                if (active.isEmpty()) {
                    ACTIVE_COMPUTATIONS.remove();
                }
            }
        }
    }

    private void disposeDepSubscriptions() {
        for (Disposable d : depSubscriptions) {
            d.dispose();
        }
        depSubscriptions.clear();
    }

    @Override
    public void dispose() {
        synchronized (lock) {
            disposed = true;
            disposeDepSubscriptions();
            listeners.clear();
            derivedDependents.clear();
        }
    }
}
