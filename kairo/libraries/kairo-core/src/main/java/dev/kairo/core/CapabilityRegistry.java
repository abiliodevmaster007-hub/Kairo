package dev.kairo.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Neutral platform capability contract. Core never imports Android or Desktop APIs.
 */
public final class CapabilityRegistry {
    public interface Capability {
        String id();
        boolean isAvailable();
    }

    private final Map<String, Capability> capabilities = new ConcurrentHashMap<>();

    public void register(Capability capability) {
        capabilities.put(capability.id(), capability);
    }

    @SuppressWarnings("unchecked")
    public <T extends Capability> Optional<T> find(String id, Class<T> type) {
        Capability cap = capabilities.get(id);
        if (cap != null && type.isInstance(cap)) {
            return Optional.of((T) cap);
        }
        return Optional.empty();
    }

    public boolean isAvailable(String id) {
        Capability cap = capabilities.get(id);
        return cap != null && cap.isAvailable();
    }
}
