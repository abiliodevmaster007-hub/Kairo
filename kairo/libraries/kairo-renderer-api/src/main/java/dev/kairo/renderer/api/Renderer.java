package dev.kairo.renderer.api;

import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutEngine;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Contract implemented by AndroidRenderer (v1) and FakeRenderer (testkit) or SkiaRenderer (v2).
 */
public interface Renderer {
    record Capabilities(
            boolean supportsHover,
            boolean supportsNativeVirtualization,
            boolean supportsTransitions,
            String backendName
    ) {}

    Capabilities capabilities();

    LayoutEngine.LeafMeasurer leafMeasurer();

    RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink);

    void apply(List<RenderPatch> patchBatch);

    void unmountRoot(KairoId rootId);

    Set<KairoId> mountedIds();

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
