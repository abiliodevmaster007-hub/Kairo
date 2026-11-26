package dev.kairo.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Logical identity inside Kairo's virtual UI tree. Distinct from native renderer handles.
 */
public record KairoId(long value, String logicalId) {
    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    public KairoId {
        if (value <= 0) {
            throw new IllegalArgumentException("KairoId value must be positive");
        }
    }

    public static KairoId next(String logicalId) {
        return new KairoId(SEQUENCE.getAndIncrement(), logicalId);
    }

    public static KairoId anonymous() {
        return new KairoId(SEQUENCE.getAndIncrement(), null);
    }

    public boolean hasLogicalId() {
        return logicalId != null && !logicalId.isBlank();
    }

    @Override
    public String toString() {
        return hasLogicalId() ? "KairoId#" + logicalId + "(" + value + ")" : "KairoId(" + value + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KairoId other)) return false;
        return value == other.value && Objects.equals(logicalId, other.logicalId);
    }
}
