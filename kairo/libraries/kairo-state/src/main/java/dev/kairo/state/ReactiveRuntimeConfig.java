package dev.kairo.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe reactive runtime configuration controlling UI scheduler affinity,
 * background mutation ConcurrencyPolicy, and isolated ReactiveErrorHandler reporting.
 */
public final class ReactiveRuntimeConfig {
    private static final AtomicReference<UiScheduler> UI_SCHEDULER =
            new AtomicReference<>(UiScheduler.IMMEDIATE);
    private static final AtomicReference<ConcurrencyPolicy> CONCURRENCY_POLICY =
            new AtomicReference<>(ConcurrencyPolicy.DISPATCH_TO_UI);
    private static final AtomicReference<ReactiveErrorHandler> ERROR_HANDLER =
            new AtomicReference<>(ReactiveErrorHandler.PRINT_STDERR);

    private ReactiveRuntimeConfig() {}

    public static UiScheduler uiScheduler() {
        return UI_SCHEDULER.get();
    }

    public static void setUiScheduler(UiScheduler scheduler) {
        UI_SCHEDULER.set(Objects.requireNonNull(scheduler, "scheduler cannot be null"));
    }

    public static ConcurrencyPolicy concurrencyPolicy() {
        return CONCURRENCY_POLICY.get();
    }

    public static void setConcurrencyPolicy(ConcurrencyPolicy policy) {
        CONCURRENCY_POLICY.set(Objects.requireNonNull(policy, "policy cannot be null"));
    }

    public static ReactiveErrorHandler errorHandler() {
        return ERROR_HANDLER.get();
    }

    public static void setErrorHandler(ReactiveErrorHandler handler) {
        ERROR_HANDLER.set(Objects.requireNonNull(handler, "handler cannot be null"));
    }

    public static void resetDefaults() {
        UI_SCHEDULER.set(UiScheduler.IMMEDIATE);
        CONCURRENCY_POLICY.set(ConcurrencyPolicy.DISPATCH_TO_UI);
        ERROR_HANDLER.set(ReactiveErrorHandler.PRINT_STDERR);
    }

    static boolean checkUiThreadOrDispatch(Runnable uiTask, String actionLabel) {
        ConcurrencyPolicy policy = CONCURRENCY_POLICY.get();
        if (policy == ConcurrencyPolicy.IMMEDIATE) {
            return true;
        }
        UiScheduler scheduler = UI_SCHEDULER.get();
        if (scheduler.isUiThread()) {
            return true;
        }
        if (policy == ConcurrencyPolicy.STRICT_THROW) {
            throw new IllegalStateException(
                    actionLabel + " attempted off UI thread [" + Thread.currentThread().getName()
                            + "] while STRICT_THROW policy is active."
            );
        }
        scheduler.execute(uiTask);
        return false;
    }

    static void reportListenerError(String source, Throwable error) {
        ReactiveErrorHandler handler = ERROR_HANDLER.get();
        try {
            handler.onListenerError(source, error);
        } catch (RuntimeException ignored) {
            // Never allow a faulty error handler to disrupt remaining reactive flush delivery.
        }
    }

    static void reportDerivedError(Derived<?> derived, Throwable error) {
        ReactiveErrorHandler handler = ERROR_HANDLER.get();
        try {
            handler.onDerivedError(derived, error);
        } catch (RuntimeException ignored) {
            // Best-effort isolation
        }
    }

    static void reportTransactionError(Throwable error) {
        ReactiveErrorHandler handler = ERROR_HANDLER.get();
        try {
            handler.onTransactionError(error);
        } catch (RuntimeException ignored) {
            // Best-effort isolation
        }
    }
}
