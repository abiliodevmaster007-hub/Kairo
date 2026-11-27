package dev.kairo.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class StateConcurrencyAndIsolationTest {

    @AfterEach
    void restoreDefaults() {
        ReactiveRuntimeConfig.resetDefaults();
    }

    @Test
    void backgroundMutationRedirectsToUiSchedulerUnderDispatchPolicy() throws Exception {
        AtomicReference<Thread> uiThreadRef = new AtomicReference<>();
        ScheduledExecutorService uiExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "kairo-test-ui");
            uiThreadRef.set(t);
            return t;
        });

        try {
            UiScheduler testUi = new UiScheduler() {
                @Override
                public boolean isUiThread() {
                    return Thread.currentThread() == uiThreadRef.get();
                }

                @Override
                public void execute(Runnable task) {
                    uiExecutor.execute(task);
                }

                @Override
                public void executeDelayed(Runnable task, long delayMillis) {
                    uiExecutor.schedule(task, Math.max(0L, delayMillis), TimeUnit.MILLISECONDS);
                }
            };

            ReactiveRuntimeConfig.setUiScheduler(testUi);
            ReactiveRuntimeConfig.setConcurrencyPolicy(ConcurrencyPolicy.DISPATCH_TO_UI);

            State<Integer> counter = State.of(0);
            List<String> listenerThreads = new CopyOnWriteArrayList<>();
            CountDownLatch observedLatch = new CountDownLatch(20);
            counter.subscribe(val -> {
                listenerThreads.add(Thread.currentThread().getName());
                observedLatch.countDown();
            });

            ExecutorService workers = Executors.newFixedThreadPool(4);
            CountDownLatch workersDone = new CountDownLatch(20);
            for (int i = 0; i < 20; i++) {
                workers.submit(() -> {
                    counter.update(n -> n + 1);
                    workersDone.countDown();
                });
            }

            assertThat(workersDone.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(observedLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(counter.peek()).isEqualTo(20);
            assertThat(listenerThreads).hasSize(20).containsOnly("kairo-test-ui");
            workers.shutdownNow();
        } finally {
            uiExecutor.shutdownNow();
        }
    }

    @Test
    void strictThrowRejectsOffThreadMutationsWithoutChangingState() throws Exception {
        Thread mainThread = Thread.currentThread();
        ReactiveRuntimeConfig.setUiScheduler(new UiScheduler() {
            @Override
            public boolean isUiThread() {
                return Thread.currentThread() == mainThread;
            }

            @Override
            public void execute(Runnable task) {
                task.run();
            }

            @Override
            public void executeDelayed(Runnable task, long delayMillis) {
                task.run();
            }
        });
        ReactiveRuntimeConfig.setConcurrencyPolicy(ConcurrencyPolicy.STRICT_THROW);

        State<String> label = State.of("initial");
        ObservableList<String> list = ObservableList.of(List.of("a"));
        AtomicReference<Throwable> stateFailure = new AtomicReference<>();
        AtomicReference<Throwable> listFailure = new AtomicReference<>();

        CountDownLatch done = new CountDownLatch(1);
        Thread bg = new Thread(() -> {
            try {
                label.set("mutated-bg");
            } catch (Throwable t) {
                stateFailure.set(t);
            }
            try {
                list.add("b");
            } catch (Throwable t) {
                listFailure.set(t);
            }
            done.countDown();
        }, "worker-thread");
        bg.start();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(stateFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRICT_THROW");
        assertThat(listFailure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("STRICT_THROW");
        assertThat(label.peek()).isEqualTo("initial");
        assertThat(list.snapshot()).containsExactly("a");
    }

    @Test
    void listenerExceptionIsolationReportsErrorAndContinuesDelivery() {
        List<String> reportedErrors = new ArrayList<>();
        ReactiveRuntimeConfig.setErrorHandler((source, err) -> reportedErrors.add(source + ":" + err.getMessage()));

        State<Integer> count = State.of(0);
        Derived<Integer> doubled = Derived.derive(() -> count.get() * 2);
        List<Integer> healthyLog = new ArrayList<>();
        List<Integer> derivedHealthyLog = new ArrayList<>();

        count.subscribe(v -> {
            throw new IllegalStateException("boom-state-" + v);
        });
        count.subscribe(healthyLog::add);

        doubled.subscribe(v -> {
            throw new IllegalArgumentException("boom-derived-" + v);
        });
        doubled.subscribe(derivedHealthyLog::add);

        State.transaction(() -> count.set(3));
        assertThat(healthyLog).containsExactly(3);
        assertThat(derivedHealthyLog).containsExactly(6);
        assertThat(reportedErrors).containsExactlyInAnyOrder(
                "State.transaction.listener:boom-state-3",
                "State.transaction.listener:boom-derived-6"
        );

        // Next transaction still executes cleanly without poisoned ThreadLocal
        State.transaction(() -> count.set(5));
        assertThat(healthyLog).containsExactly(3, 5);
        assertThat(derivedHealthyLog).containsExactly(6, 10);

        // Immediate set outside transaction also isolates listener errors
        count.set(7);
        assertThat(healthyLog).containsExactly(3, 5, 7);
        assertThat(derivedHealthyLog).containsExactly(6, 10, 14);

        doubled.dispose();

        // Explicit cycle protection remains strict
        assertThatThrownBy(() -> State.transaction(new Runnable() {
            @Override
            public void run() {
                count.subscribe(v -> {
                    if (v < 200) count.set(v + 1);
                });
                count.set(10);
            }
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum flush rounds");
    }
}
