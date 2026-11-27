package dev.kairo.platform.android;

import android.os.Handler;
import android.os.Looper;
import dev.kairo.async.FutureState;
import dev.kairo.async.KairoSchedulers;
import dev.kairo.state.ReactiveRuntimeConfig;
import dev.kairo.state.UiScheduler;

/**
 * Dispatches Kairo FutureState and State UI updates safely onto Android's main Looper thread
 * without exposing android.os.* types to kairo-state, kairo-async, or any neutral module.
 */
public final class AndroidMainScheduler implements FutureState.Scheduler, UiScheduler {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AndroidMainScheduler install() {
        AndroidMainScheduler scheduler = new AndroidMainScheduler();
        KairoSchedulers.setUiScheduler(scheduler);
        ReactiveRuntimeConfig.setUiScheduler(scheduler);
        return scheduler;
    }

    public boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    @Override
    public boolean isUiThread() {
        return isMainThread();
    }

    @Override
    public void execute(Runnable task) {
        if (task == null) return;
        if (isMainThread()) {
            task.run();
        } else {
            mainHandler.post(task);
        }
    }

    @Override
    public void executeDelayed(Runnable task, long delayMillis) {
        if (task == null) return;
        if (delayMillis <= 0L) {
            execute(task);
            return;
        }
        mainHandler.postDelayed(task, delayMillis);
    }
}
