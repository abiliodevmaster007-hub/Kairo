package dev.kairo.async;

import dev.kairo.lifecycle.Disposable;
import dev.kairo.lifecycle.LifecycleScope;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative thread-safe cancellation token bound optionally to a LifecycleScope.
 */
public final class CancellationToken {
    public static final class AsyncCancelledException extends CancellationException {
        public AsyncCancelledException(String message) {
            super(message);
        }
    }

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new ArrayList<>();

    private CancellationToken() {}

    public static CancellationToken create() {
        return new CancellationToken();
    }

    public static CancellationToken linkedTo(LifecycleScope scope) {
        CancellationToken token = new CancellationToken();
        if (scope == null) {
            return token;
        }
        if (scope.isDestroyed()) {
            token.cancel();
            return token;
        }
        scope.ownForMount(token::cancel);
        return token;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void throwIfCancelled() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new AsyncCancelledException("Async operation cancelled");
        }
    }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        List<Runnable> toRun;
        synchronized (listeners) {
            toRun = List.copyOf(listeners);
            listeners.clear();
        }
        for (Runnable listener : toRun) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // Cooperative cancellation must always complete all remaining hooks.
            }
        }
        return true;
    }

    public Disposable onCancel(Runnable listener) {
        if (listener == null) {
            return Disposable.empty();
        }
        synchronized (listeners) {
            if (cancelled.get()) {
                listener.run();
                return Disposable.empty();
            }
            listeners.add(listener);
            return () -> {
                synchronized (listeners) {
                    listeners.remove(listener);
                }
            };
        }
    }

    public CancellationToken createChild() {
        CancellationToken child = new CancellationToken();
        onCancel(child::cancel);
        return child;
    }
}
