package dev.kairo.renderer.api;

import dev.kairo.core.KairoId;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;

import java.util.Map;
import java.util.Set;

/**
 * Granular incremental mutations dispatched from Kairo Runtime to any platform Renderer.
 */
public sealed interface RenderPatch {
    record CreateNode(KairoId id, String componentType, Map<String, String> props, ComputedStyle style) implements RenderPatch {}
    record UpdateProp(KairoId id, String property, String value) implements RenderPatch {}
    record UpdateStyle(KairoId id, ComputedStyle style) implements RenderPatch {}
    record UpdateBounds(KairoId id, LayoutBounds bounds) implements RenderPatch {}
    record UpdateVisualStates(KairoId id, Set<VisualState> states) implements RenderPatch {}
    record InsertChild(KairoId parentId, KairoId childId, int index) implements RenderPatch {}
    record MoveChild(KairoId parentId, KairoId childId, int fromIndex, int toIndex) implements RenderPatch {}
    record RemoveNode(KairoId id) implements RenderPatch {}
}
