package dev.kairo.platform.android;

import android.os.Handler;
import android.os.Looper;
import dev.kairo.async.FutureState;
import dev.kairo.async.KairoSchedulers;

/**
 * Dispatches Kairo FutureState UI updates safely onto Android's main Looper thread
 * without exposing android.os.* types to kairo-async or any neutral module.
 */
public final class AndroidMainScheduler implements FutureState.Scheduler {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static AndroidMainScheduler install() {
        AndroidMainScheduler scheduler = new AndroidMainScheduler();
        KairoSchedulers.setUiScheduler(scheduler);
        return scheduler;
    }

    public boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
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
}
