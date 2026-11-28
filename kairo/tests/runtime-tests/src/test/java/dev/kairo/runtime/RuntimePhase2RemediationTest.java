package dev.kairo.runtime;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.core.KairoId;
import dev.kairo.css.CssStylesheet;
import dev.kairo.events.CommonEvents;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.renderer.api.RenderHandle;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.state.State;
import dev.kairo.style.ComputedStyle;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class RuntimePhase2RemediationTest {

    @Test
    void enforcesLifecycleStateMachineTransitionsIdempotencyAndPostDisposeGuards() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse(
                "lifecycle.xml",
                "<Screen id=\"s\"><Button id=\"btn\" onClick=\"go\">OK</Button></Screen>"
        );
        KairoRuntime.ScreenModel model = modelOf(Map.of(), Map.of(), Map.of("go", () -> {}));

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.setStrictConsistencyChecks(true);

        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.UNMOUNTED);

        // Idempotent unmount before mount does not fail
        runtime.unmount();
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.UNMOUNTED);

        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);
        runtime.verifyRendererConsistency();

        runtime.unmount();
        runtime.unmount(); // Idempotent second unmount
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.UNMOUNTED);
        assertThat(renderer.mountedIds()).isEmpty();
        runtime.verifyRendererConsistency();

        // Remount cleanly transitions UNMOUNTED -> MOUNTED
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);

        runtime.destroy();
        runtime.destroy(); // Idempotent second destroy
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.DISPOSED);

        assertThatThrownBy(() -> runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destroyed");
        assertThatThrownBy(() -> runtime.setTheme("dark"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("destroyed");
    }

    @Test
    void detectsRendererDivergenceViaConsistencyVerifier() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse(
                "consistency.xml",
                "<Screen id=\"s\"><Column id=\"c\"><Text id=\"t\" value=\"Hello\"/></Column></Screen>"
        );
        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(parsed.root(), CssStylesheet.empty(), modelOf(Map.of(), Map.of(), Map.of()), Fixtures.STANDARD_PHONE);

        runtime.verifyRendererConsistency();

        // Corrupt renderer directly by injecting an extra orphan node
        KairoId orphanId = KairoId.next("orphan");
        renderer.apply(List.of(new RenderPatch.CreateNode(orphanId, "Text", Map.of(), ComputedStyle.empty())));
        assertThatThrownBy(runtime::verifyRendererConsistency)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Renderer divergence");

        runtime.destroy();
    }

    @Test
    void preservesPreviousTreeAndEventRoutingWhenRecomputeExpressionThrows() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse(
                "failbuild.xml",
                "<Screen id=\"s\"><Column id=\"c\"><Text id=\"lbl\" value=\"{{ label }}\"/><Button id=\"btn\" onClick=\"hit\">Hit</Button></Column></Screen>"
        );

        AtomicBoolean shouldThrow = new AtomicBoolean(false);
        AtomicInteger hits = new AtomicInteger();
        KairoRuntime.ScreenModel model = modelOf(
                Map.of("label", () -> {
                    if (shouldThrow.get()) {
                        throw new IllegalStateException("binding exploded");
                    }
                    return "Healthy";
                }),
                Map.of(),
                Map.of("hit", hits::incrementAndGet)
        );

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(renderer.findByLogicalId("lbl").props.get("value")).isEqualTo("Healthy");

        // Trigger failing recompute -> must preserve existing tree, nodeIndex, and MOUNTED state
        shouldThrow.set(true);
        assertThatThrownBy(runtime::recompute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("binding exploded");

        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);
        assertThat(runtime.currentTree()).isNotNull();
        runtime.verifyRendererConsistency();

        // Interactive events on existing tree remain fully functional
        renderer.dispatch(new CommonEvents.ClickEvent(renderer.findByLogicalId("btn").id()));
        assertThat(hits.get()).isEqualTo(1);

        // Next recompute succeeds seamlessly once failure clears
        shouldThrow.set(false);
        runtime.recompute();
        assertThat(renderer.findByLogicalId("lbl").props.get("value")).isEqualTo("Healthy");
        runtime.verifyRendererConsistency();

        runtime.destroy();
    }

    @Test
    void rollsBackIncrementalPatchFailureWithoutCorruptingRendererOrPatchHistory() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse(
                "rollback.xml",
                "<Screen id=\"s\"><Text id=\"t\" value=\"{{ v }}\"/></Screen>"
        );

        AtomicReference<String> v = new AtomicReference<>("v1");
        KairoRuntime.ScreenModel model = modelOf(Map.of("v", v::get), Map.of(), Map.of());

        FakeRenderer delegate = new FakeRenderer();
        AtomicBoolean failNextUpdate = new AtomicBoolean(false);
        Renderer flakyRenderer = new Renderer() {
            @Override public Capabilities capabilities() { return delegate.capabilities(); }
            @Override public LayoutEngine.LeafMeasurer leafMeasurer() { return delegate.leafMeasurer(); }
            @Override public RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink) {
                return delegate.mountRoot(rootId, componentType, eventSink);
            }
            @Override public void apply(List<RenderPatch> patchBatch) {
                if (failNextUpdate.compareAndSet(true, false)) {
                    throw new IllegalStateException("Mid-batch apply failure");
                }
                delegate.apply(patchBatch);
            }
            @Override public void unmountRoot(KairoId rootId) { delegate.unmountRoot(rootId); }
            @Override public Set<KairoId> mountedIds() { return delegate.mountedIds(); }
            @Override public RendererTreeSnapshot inspectTree() { return delegate.inspectTree(); }
        };

        KairoRuntime runtime = new KairoRuntime(registry, flakyRenderer);
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        int historyBeforeFail = runtime.patchHistory().size();

        // Simulate incremental patch apply failure (updating backing value without State listener catch)
        failNextUpdate.set(true);
        v.set("v2");
        assertThatThrownBy(runtime::recompute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Mid-batch apply failure");

        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);
        assertThat(runtime.patchHistory().size()).isEqualTo(historyBeforeFail);
        assertThat(delegate.findByLogicalId("t").props.get("value")).isEqualTo("v1");
        runtime.verifyRendererConsistency();

        // Next recompute reconciles cleanly to v2
        runtime.recompute();
        assertThat(delegate.findByLogicalId("t").props.get("value")).isEqualTo("v2");
        runtime.verifyRendererConsistency();

        runtime.destroy();
    }

    @Test
    void boundsReentrantRecomputesAndResetsStateCleanly() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse(
                "reentrant.xml",
                "<Screen id=\"s\"><Text id=\"t\" value=\"{{ loop }}\"/></Screen>"
        );

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime[] holder = new KairoRuntime[1];
        AtomicBoolean triggerLoop = new AtomicBoolean(false);
        KairoRuntime.ScreenModel model = modelOf(
                Map.of("loop", () -> {
                    if (triggerLoop.get() && holder[0] != null) {
                        holder[0].recompute();
                    }
                    return "ok";
                }),
                Map.of(),
                Map.of()
        );

        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        holder[0] = runtime;
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);

        triggerLoop.set(true);
        assertThatThrownBy(runtime::recompute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("maximum reentrant recompute passes");
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);

        triggerLoop.set(false);
        runtime.recompute();
        assertThat(runtime.state()).isEqualTo(KairoRuntime.RuntimeState.MOUNTED);
        runtime.destroy();
    }

    private static KairoRuntime.ScreenModel modelOf(
            Map<String, Supplier<Object>> bindings,
            Map<String, State<String>> twoWay,
            Map<String, Runnable> actions) {
        return new KairoRuntime.ScreenModel() {
            @Override
            public Map<String, Supplier<Object>> exposedBindings() { return bindings; }

            @Override
            public Map<String, State<String>> twoWayStringStates() { return twoWay; }

            @Override
            public Map<String, Runnable> actions() { return actions; }
        };
    }
}
