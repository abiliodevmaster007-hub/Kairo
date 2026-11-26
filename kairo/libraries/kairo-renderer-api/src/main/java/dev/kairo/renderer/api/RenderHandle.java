package dev.kairo.renderer.api;

import dev.kairo.core.KairoId;

/**
 * Opaque renderer handle. Keeps native Android View / v2 surface types completely hidden from Core and components.
 */
public interface RenderHandle {
    KairoId id();
    String componentType();
}
