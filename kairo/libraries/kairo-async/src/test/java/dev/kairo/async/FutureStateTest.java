package dev.kairo.async;

import dev.kairo.async.FutureState.Status;
import dev.kairo.lifecycle.LifecycleScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

final class FutureStateTest {
    private static final class ManualScheduler implements FutureState.Scheduler {
        private final Deque<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable task) {
            queue.addLast(task);
        }

        boolean runNext() {
            Runnable next = queue.pollFirst();
            if (next == null) return false;
            next.run();
            return true;
        }

        void flushAll() {
            while (runNext()) {
                // drain queued work
            }
        }
    }

    @AfterEach
    void resetGlobalSchedulers() {
        KairoSchedulers.resetUiScheduler();
    }

    @Test
    void runsFromLoadingToSuccessWithDerivedFlagsAndPostCompletionHook() {
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        ManualScheduler worker = new ManualScheduler();
        ManualScheduler ui = new ManualScheduler();

        List<String> callbacks = new ArrayList<>();
        FutureState<String> future = FutureState.run(scope, worker, ui, () -> "Kairo Ready")
                .onSuccess(v -> callbacks.add("pre:" + v));

        assertThat(future.status.get()).isEqualTo(Status.RUNNING);
        assertThat(future.isLoading.get()).isTrue();
        assertThat(future.isDone.get()).isFalse();
        assertThat(future.snapshot()).isInstanceOf(AsyncResult.Loading.class);

        worker.flushAll();
        assertThat(future.status.get()).isEqualTo(Status.RUNNING);

        ui.flushAll();
        assertThat(future.status.get()).isEqualTo(Status.SUCCESS);
        assertThat(future.value.get()).isEqualTo("Kairo Ready");
        assertThat(future.valueOr("fallback")).isEqualTo("Kairo Ready");
        assertThat(future.isSuccess.get()).isTrue();
        assertThat(future.isDone.get()).isTrue();
        assertThat(future.snapshot()).isEqualTo(new AsyncResult.Success<>("Kairo Ready"));

        // Synchronous immediate replay for listeners registered after completion
        future.onSuccess(v -> callbacks.add("post:" + v));
        assertThat(callbacks).containsExactly("pre:Kairo Ready", "post:Kairo Ready");
    }

    @Test
    void handlesFailureAndRecoversOnRetry() {
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        AtomicInteger attempt = new AtomicInteger();

        FutureState<Integer> future = FutureState.run(
                scope,
                FutureState.Schedulers.IMMEDIATE,
                FutureState.Schedulers.IMMEDIATE,
                token -> {
                    if (attempt.incrementAndGet() == 1) {
                        throw new IllegalStateException("offline");
                    }
                    return 42;
                }
        );

        assertThat(future.status.get()).isEqualTo(Status.ERROR);
        assertThat(future.isError.get()).isTrue();
        assertThat(future.error.get()).hasMessage("offline");
        assertThat(future.snapshot()).isInstanceOf(AsyncResult.Failure.class);

        future.retry();
        assertThat(future.status.get()).isEqualTo(Status.SUCCESS);
        assertThat(future.value.get()).isEqualTo(42);
    }

    @Test
    void unmountCancelsInFlightWorkAndPreventsLateStateOverwrite() {
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        ManualScheduler worker = new ManualScheduler();
        ManualScheduler ui = new ManualScheduler();
        AtomicInteger cancelNotifications = new AtomicInteger();

        FutureState<String> future = FutureState.run(scope, worker, ui, token -> {
            token.throwIfCancelled();
            return "stale-result";
        }).onCancel(cancelNotifications::incrementAndGet);

        scope.onUnmount();

        assertThat(future.status.get()).isEqualTo(Status.CANCELLED);
        assertThat(future.isCancelled.get()).isTrue();
        assertThat(future.currentToken().isCancelled()).isTrue();
        assertThat(cancelNotifications.get()).isEqualTo(1);

        worker.flushAll();
        ui.flushAll();
        assertThat(future.status.get()).isEqualTo(Status.CANCELLED);
        assertThat(future.value.get()).isNull();
    }

    @Test
    void reloadBumpsGenerationSoStaleResponseCannotOverwriteNewerResult() {
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        ManualScheduler worker = new ManualScheduler();
        ManualScheduler ui = new ManualScheduler();

        FutureState<String> future = FutureState.create(scope, worker, ui);
        future.launch(token -> "v1-old");

        // Reload before v1 completes
        future.launch(token -> "v2-fresh");

        // Complete both workers; stale v1 is ignored even on flush
        worker.flushAll();
        ui.flushAll();

        assertThat(future.status.get()).isEqualTo(Status.SUCCESS);
        assertThat(future.value.get()).isEqualTo("v2-fresh");
    }

    @Test
    void launchingOnDestroyedScopeImmediatelyCancelsWithoutRunningWork() {
        LifecycleScope scope = new LifecycleScope();
        scope.onDestroy();
        AtomicInteger executions = new AtomicInteger();

        FutureState<String> future = FutureState.run(
                scope,
                FutureState.Schedulers.IMMEDIATE,
                FutureState.Schedulers.IMMEDIATE,
                () -> {
                    executions.incrementAndGet();
                    return "never";
                }
        );

        assertThat(future.status.get()).isEqualTo(Status.CANCELLED);
        assertThat(executions.get()).isZero();
    }

    @Test
    void supportsNeutralUiSchedulerRegistration() {
        ManualScheduler customUi = new ManualScheduler();
        KairoSchedulers.setUiScheduler(customUi);
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();

        FutureState<String> future = FutureState.run(
                scope,
                FutureState.Schedulers.IMMEDIATE,
                KairoSchedulers.ui(),
                () -> "dispatched"
        );

        assertThat(future.status.get()).isEqualTo(Status.RUNNING);
        customUi.flushAll();
        assertThat(future.status.get()).isEqualTo(Status.SUCCESS);
        assertThat(future.value.get()).isEqualTo("dispatched");
    }

    @Test
    void disposeCancelsWorkAndDisposesInternalDerivedFlags() {
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        ManualScheduler worker = new ManualScheduler();
        ManualScheduler ui = new ManualScheduler();

        FutureState<String> future = FutureState.run(scope, worker, ui, token -> "done");
        future.dispose();

        assertThat(future.status.get()).isEqualTo(Status.CANCELLED);
        assertThat(future.isIdle.isDisposed()).isTrue();
        assertThat(future.isLoading.isDisposed()).isTrue();
        assertThat(future.isSuccess.isDisposed()).isTrue();
        assertThat(future.isError.isDisposed()).isTrue();
        assertThat(future.isCancelled.isDisposed()).isTrue();
        assertThat(future.isDone.isDisposed()).isTrue();
    }
}
