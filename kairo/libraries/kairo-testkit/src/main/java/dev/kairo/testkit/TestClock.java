package dev.kairo.testkit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Deterministic controllable clock for Kairo TestKit.
 */
public final class TestClock {
    private final AtomicLong currentMillis;

    public TestClock(long initialMillis) {
        this.currentMillis = new AtomicLong(initialMillis);
    }

    public TestClock() {
        this(0L);
    }

    public long nowMillis() {
        return currentMillis.get();
    }

    public void advanceBy(Duration duration) {
        currentMillis.addAndGet(duration.toMillis());
    }

    public void setMillis(long millis) {
        currentMillis.set(millis);
    }
}
