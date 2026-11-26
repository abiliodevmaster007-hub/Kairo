package dev.kairo.core;

import java.util.Objects;

/**
 * Stable item identity for dynamic collections/lists, distinct from component ID.
 */
public record StableKey(String value) {
    public StableKey {
        Objects.requireNonNull(value, "StableKey cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("StableKey cannot be blank");
        }
    }

    public static StableKey of(Object key) {
        return new StableKey(String.valueOf(key));
    }
}
