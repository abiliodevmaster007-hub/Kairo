package dev.kairo.testkit;

import dev.kairo.async.FutureState;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Deterministic manual-advance scheduler implementing FutureState.Scheduler for predictable async testing.
 */
public final class TestScheduler implements FutureState.Scheduler {
    private final Deque<Runnable> queue = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
        queue.addLast(task);
    }

    public int pendingCount() {
        return queue.size();
    }

    public boolean runNext() {
        Runnable next = queue.pollFirst();
        if (next == null) return false;
        next.run();
        return true;
    }

    public int flushAll() {
        int count = 0;
        while (runNext()) {
            count++;
        }
        return count;
    }
}
