package dev.kairo.components.api;

import dev.kairo.ir.NodeIR;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Explicit component registry. Prohibits classpath reflection scanning (avoiding Reflections library).
 */
public final class ComponentRegistry {
    @FunctionalInterface
    public interface CustomComponentExpander {
        NodeIR expand(NodeIR invocation, Map<String, NodeIR> namedSlots);
    }

    @FunctionalInterface
    public interface Registrar {
        void registerInto(ComponentRegistry registry);
    }

    private final Map<String, ComponentDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, CustomComponentExpander> customExpanders = new LinkedHashMap<>();

    public void register(ComponentDescriptor descriptor) {
        descriptors.put(descriptor.tag(), descriptor);
    }

    public void registerCustom(ComponentDescriptor descriptor, CustomComponentExpander expander) {
        descriptors.put(descriptor.tag(), descriptor);
        customExpanders.put(descriptor.tag(), expander);
    }

    public void registerModule(Registrar... registrars) {
        if (registrars == null) return;
        for (Registrar r : registrars) {
            if (r != null) r.registerInto(this);
        }
    }

    public boolean contains(String tag) {
        return descriptors.containsKey(tag);
    }

    public Optional<ComponentDescriptor> find(String tag) {
        return Optional.ofNullable(descriptors.get(tag));
    }

    public Optional<CustomComponentExpander> findExpander(String tag) {
        return Optional.ofNullable(customExpanders.get(tag));
    }

    public Map<String, ComponentDescriptor> all() {
        return Collections.unmodifiableMap(descriptors);
    }
}
