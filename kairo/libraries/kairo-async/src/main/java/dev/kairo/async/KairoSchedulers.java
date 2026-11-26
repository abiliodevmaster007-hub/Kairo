package dev.kairo.async;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Neutral bounded schedulers for Kairo async execution.
 * Platform hosts (such as Android main Looper) can install the UI dispatcher at startup
 * without any platform import in kairo-async.
 */
public final class KairoSchedulers {
    private static final ExecutorService IO_POOL = Executors.newFixedThreadPool(
            4,
            daemonThreadFactory("kairo-io")
    );

    private static final ExecutorService COMPUTE_POOL = Executors.newFixedThreadPool(
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors())),
            daemonThreadFactory("kairo-compute")
    );

    private static final FutureState.Scheduler IMMEDIATE = Runnable::run;
    private static final FutureState.Scheduler IO = IO_POOL::execute;
    private static final FutureState.Scheduler COMPUTE = COMPUTE_POOL::execute;

    private static final AtomicReference<FutureState.Scheduler> UI_SCHEDULER =
            new AtomicReference<>(IMMEDIATE);

    private KairoSchedulers() {}

    public static FutureState.Scheduler io() {
        return IO;
    }

    public static FutureState.Scheduler compute() {
        return COMPUTE;
    }

    public static FutureState.Scheduler immediate() {
        return IMMEDIATE;
    }

    public static FutureState.Scheduler ui() {
        return UI_SCHEDULER.get();
    }

    public static void setUiScheduler(FutureState.Scheduler scheduler) {
        UI_SCHEDULER.set(Objects.requireNonNull(scheduler, "UI scheduler cannot be null"));
    }

    public static void resetUiScheduler() {
        UI_SCHEDULER.set(IMMEDIATE);
    }

    private static ThreadFactory daemonThreadFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread t = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
    }
}
