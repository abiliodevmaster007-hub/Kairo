package dev.kairo.renderer.api;

import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutEngine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Contract implemented by AndroidRenderer (v1), FakeRenderer (testkit), or SkiaRenderer (v2).
 * Implementations must guarantee recursive subtree cleanup whenever a {@link RenderPatch.RemoveNode}
 * is applied, and support repeated {@link #mountRoot}/{@link #unmountRoot} transitions cleanly.
 */
public interface Renderer {
    record Capabilities(
            boolean supportsHover,
            boolean supportsNativeVirtualization,
            boolean supportsTransitions,
            String backendName
    ) {}

    record RendererNodeSnapshot(
            KairoId id,
            String componentType,
            List<KairoId> childIds
    ) {
        public RendererNodeSnapshot {
            childIds = childIds != null ? List.copyOf(childIds) : List.of();
        }
    }

    record RendererTreeSnapshot(
            KairoId rootId,
            Map<KairoId, RendererNodeSnapshot> nodes
    ) {
        public RendererTreeSnapshot {
            nodes = nodes != null ? Map.copyOf(nodes) : Map.of();
        }
    }

    Capabilities capabilities();

    LayoutEngine.LeafMeasurer leafMeasurer();

    RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink);

    void apply(List<RenderPatch> patchBatch);

    void unmountRoot(KairoId rootId);

    Set<KairoId> mountedIds();

    default RendererTreeSnapshot inspectTree() {
        return null;
    }

    default RenderEngineStats engineStats() {
        return new RenderEngineStats(
                capabilities().backendName(),
                false,
                mountedIds().size(),
                0,
                0L,
                0L
        );
    }

    default void flushFrame() {}
}
