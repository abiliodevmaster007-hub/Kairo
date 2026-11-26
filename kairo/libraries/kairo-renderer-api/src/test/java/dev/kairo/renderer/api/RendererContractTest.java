package dev.kairo.renderer.api;

import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

final class RendererContractTest {

    @Test
    void exposesDefaultEngineStatsAndCapabilitiesNeutrally() {
        Renderer stub = new Renderer() {
            @Override
            public Capabilities capabilities() {
                return new Capabilities(false, true, false, "neutral-stub");
            }

            @Override
            public LayoutEngine.LeafMeasurer leafMeasurer() {
                return (n, c) -> new LayoutEngine.MeasuredSize(10, 10);
            }

            @Override
            public RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink) {
                return null;
            }

            @Override
            public void apply(List<RenderPatch> patchBatch) {}

            @Override
            public void unmountRoot(KairoId rootId) {}

            @Override
            public Set<KairoId> mountedIds() {
                return Set.of(KairoId.next("root"));
            }
        };

        RenderEngineStats stats = stub.engineStats();
        assertThat(stats.backendName()).isEqualTo("neutral-stub");
        assertThat(stats.nativeAccelerated()).isFalse();
        assertThat(stats.mountedNodes()).isEqualTo(1);
    }
}
