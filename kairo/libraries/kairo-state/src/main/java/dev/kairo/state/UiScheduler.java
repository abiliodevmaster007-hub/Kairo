package dev.kairo.state;

/**
 * Neutral UI thread scheduler contract for reactive State mutations and delayed callbacks.
 * Implemented by platform hosts (e.g. AndroidMainScheduler) without exposing OS types to kairo-state.
 */
public interface UiScheduler {
    UiScheduler IMMEDIATE = new UiScheduler() {
        @Override
        public boolean isUiThread() {
            return true;
        }

        @Override
        public void execute(Runnable task) {
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void executeDelayed(Runnable task, long delayMillis) {
            if (task != null) {
                task.run();
            }
        }
    };

    boolean isUiThread();

    void execute(Runnable task);

    void executeDelayed(Runnable task, long delayMillis);
}
