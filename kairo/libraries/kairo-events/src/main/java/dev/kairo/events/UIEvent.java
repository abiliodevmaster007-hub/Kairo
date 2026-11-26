package dev.kairo.events;

import dev.kairo.core.KairoId;

public abstract class UIEvent {
    public enum Phase {
        NONE,
        CAPTURE,
        TARGET,
        BUBBLE
    }

    private final KairoId target;
    private KairoId currentTarget;
    private Phase phase = Phase.NONE;
    private final long timestampNanos;
    private boolean propagationStopped;
    private boolean defaultPrevented;

    protected UIEvent(KairoId target) {
        this.target = target;
        this.currentTarget = target;
        this.timestampNanos = System.nanoTime();
    }

    public KairoId target() { return target; }
    public KairoId currentTarget() { return currentTarget; }
    public void setCurrentTarget(KairoId currentTarget) { this.currentTarget = currentTarget; }

    public Phase phase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public long timestampNanos() { return timestampNanos; }

    public void stopPropagation() { this.propagationStopped = true; }
    public boolean isPropagationStopped() { return propagationStopped; }

    public void preventDefault() { this.defaultPrevented = true; }
    public boolean isDefaultPrevented() { return defaultPrevented; }
}
