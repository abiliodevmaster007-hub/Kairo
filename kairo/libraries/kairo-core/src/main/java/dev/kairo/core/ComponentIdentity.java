package dev.kairo.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Unifies logical runtime identity (KairoId) with optional StableKey for list reconciliation.
 * Enforces that component ID (tree reference) and StableKey (collection item identity) stay distinct.
 */
public record ComponentIdentity(KairoId id, StableKey stableKey) {
    public ComponentIdentity {
        Objects.requireNonNull(id, "KairoId cannot be null");
    }

    public static ComponentIdentity of(KairoId id) {
        return new ComponentIdentity(id, null);
    }

    public static ComponentIdentity keyed(KairoId id, StableKey key) {
        return new ComponentIdentity(id, Objects.requireNonNull(key, "StableKey required"));
    }

    public Optional<StableKey> optionalKey() {
        return Optional.ofNullable(stableKey);
    }
}
