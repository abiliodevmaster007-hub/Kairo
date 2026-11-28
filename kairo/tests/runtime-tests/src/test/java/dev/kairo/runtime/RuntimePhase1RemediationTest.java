package dev.kairo.runtime;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.basic.BasicUI;
import dev.kairo.core.KairoId;
import dev.kairo.core.SourceLocation;
import dev.kairo.css.CssStylesheet;
import dev.kairo.events.CommonEvents;
import dev.kairo.events.UIEvent;
import dev.kairo.ir.BindingSpec;
import dev.kairo.ir.NodeIR;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.lifecycle.Disposable;
import dev.kairo.renderer.api.RenderHandle;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.state.State;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
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

final class RuntimePhase1RemediationTest {

    @Test
    void rootReplacementWithSameLogicalIdAndDifferentComponentTypeKeepsNewRootAttached() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        ComponentDescriptor rootSwitcher = ComponentDescriptor.builder("RootSwitcher")
                .role(ComponentDescriptor.SemanticRole.CONTAINER)
                .prop("mode", String.class, "screen")
                .build();
        registry.registerCustom(rootSwitcher, (invocation, slots) -> {
            String mode = invocation.properties().getOrDefault("mode", "screen");
            if ("column".equals(mode)) {
                return BasicUI.column()
                        .child(BasicUI.text("ColumnRoot").id("columnText"))
                        .child(BasicUI.button("Click").id("columnBtn").on("onClick", "action"))
                        .build();
            }
            return BasicUI.screen("appRoot")
                    .child(BasicUI.text("ScreenRoot").id("screenText"))
                    .build();
        });

        State<String> mode = State.of("screen");
        AtomicInteger actionCalls = new AtomicInteger();
        KairoRuntime.ScreenModel model = modelOf(
                Map.of("mode", () -> mode),
                Map.of(),
                Map.of("action", actionCalls::incrementAndGet)
        );

        SourceLocation loc = new SourceLocation("switcher.xml", 1, 1);
        NodeIR rootIR = new NodeIR(
                "RootSwitcher",
                "appRoot",
                null,
                List.of(),
                Map.of(),
                Map.of("mode", BindingSpec.oneWay("mode", "mode")),
                Map.of(),
                null,
                List.of(),
                loc
        );

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(rootIR, CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);

        FakeRenderer.FakeNode screenRoot = renderer.findByLogicalId("appRoot");
        assertThat(screenRoot).isNotNull();
        assertThat(screenRoot.componentType()).isEqualTo("Screen");
        KairoId screenRootId = screenRoot.id();

        AtomicInteger oldRootClicks = new AtomicInteger();
        runtime.addEventListener(screenRootId, CommonEvents.ClickEvent.class, e -> oldRootClicks.incrementAndGet());
        renderer.dispatch(new CommonEvents.FocusEvent(screenRootId, true));
        assertThat(runtime.listenerCount(screenRootId)).isEqualTo(1);

        int historyBeforeSwap = runtime.patchHistory().size();

        // Replace root componentType (Screen -> Column) while preserving logicalId="appRoot"
        mode.set("column");

        FakeRenderer.FakeNode columnRoot = renderer.findByLogicalId("appRoot");
        assertThat(columnRoot).isNotNull();
        assertThat(columnRoot.componentType()).isEqualTo("Column");
        assertThat(renderer.mountedIds()).contains(columnRoot.id());
        assertThat(renderer.findByLogicalId("screenText")).isNull();
        assertThat(renderer.findByLogicalId("columnText")).isNotNull();

        // Old root listeners & scope must be cleared immediately and must not trigger RemoveNode during replacement
        assertThat(runtime.listenerCount(screenRootId)).isZero();
        List<RenderPatch> swapPatches = runtime.patchHistory().get(historyBeforeSwap);
        assertThat(swapPatches).noneMatch(RenderPatch.RemoveNode.class::isInstance);
        assertThat(runtime.lastMetrics().nodesCreated()).isEqualTo(3);
        assertThat(runtime.lastMetrics().nodesChanged()).isZero();

        // New root subtree handles events properly
        FakeRenderer.FakeNode columnBtn = renderer.findByLogicalId("columnBtn");
        renderer.dispatch(new CommonEvents.ClickEvent(columnBtn.id()));
        assertThat(actionCalls.get()).isEqualTo(1);
        assertThat(oldRootClicks.get()).isZero();

        // Swap back Column -> Screen cleanly
        mode.set("screen");
        FakeRenderer.FakeNode restoredRoot = renderer.findByLogicalId("appRoot");
        assertThat(restoredRoot).isNotNull();
        assertThat(restoredRoot.componentType()).isEqualTo("Screen");
        assertThat(renderer.findByLogicalId("screenText")).isNotNull();

        runtime.destroy();
    }

    @Test
    void rootReplacementRollsBackCleanlyIfRendererMountThrowsAndRecoversNextFrame() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        ComponentDescriptor rootSwitcher = ComponentDescriptor.builder("RootSwitcher")
                .role(ComponentDescriptor.SemanticRole.CONTAINER)
                .prop("mode", String.class, "a")
                .build();
        registry.registerCustom(rootSwitcher, (invocation, slots) -> {
            String mode = invocation.properties().getOrDefault("mode", "a");
            return "b".equals(mode)
                    ? BasicUI.column().child(BasicUI.text("B").id("textB")).build()
                    : BasicUI.screen("appRoot").child(BasicUI.text("A").id("textA")).build();
        });

        AtomicReference<String> mode = new AtomicReference<>("a");
        KairoRuntime.ScreenModel model = modelOf(Map.of("mode", mode::get), Map.of(), Map.of());
        SourceLocation loc = new SourceLocation("fail.xml", 1, 1);
        NodeIR rootIR = new NodeIR(
                "RootSwitcher",
                "appRoot",
                null,
                List.of(),
                Map.of(),
                Map.of("mode", BindingSpec.oneWay("mode", "mode")),
                Map.of(),
                null,
                List.of(),
                loc
        );

        FakeRenderer delegate = new FakeRenderer();
        AtomicBoolean failNextApply = new AtomicBoolean(false);
        Renderer flakyRenderer = new Renderer() {
            @Override public Capabilities capabilities() { return delegate.capabilities(); }
            @Override public LayoutEngine.LeafMeasurer leafMeasurer() { return delegate.leafMeasurer(); }
            @Override public RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink) {
                return delegate.mountRoot(rootId, componentType, eventSink);
            }
            @Override public void apply(List<RenderPatch> patchBatch) {
                if (failNextApply.compareAndSet(true, false)) {
                    throw new IllegalStateException("Simulated renderer failure");
                }
                delegate.apply(patchBatch);
            }
            @Override public void unmountRoot(KairoId rootId) { delegate.unmountRoot(rootId); }
            @Override public Set<KairoId> mountedIds() { return delegate.mountedIds(); }
        };

        KairoRuntime runtime = new KairoRuntime(registry, flakyRenderer);
        runtime.mount(rootIR, CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(delegate.findByLogicalId("appRoot").componentType()).isEqualTo("Screen");

        mode.set("b");
        failNextApply.set(true);
        assertThatThrownBy(runtime::recompute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated renderer failure");

        // Rollback left no orphan partial root mounted
        assertThat(runtime.currentTree()).isNull();
        assertThat(delegate.mountedIds()).isEmpty();

        // Next recompute recovers cleanly from rolled-back state
        runtime.recompute();
        assertThat(runtime.currentTree()).isNotNull();
        assertThat(delegate.findByLogicalId("appRoot").componentType()).isEqualTo("Column");
        assertThat(delegate.findByLogicalId("textB")).isNotNull();

        runtime.destroy();
    }

    @Test
    void nodeOwnedScopesReleaseListenerDisposersUponEvictionAndManualDispose() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        State<Boolean> showPanel = State.of(true);
        KairoRuntime.ScreenModel model = modelOf(Map.of("showPanel", () -> showPanel), Map.of(), Map.of());

        SourceLocation loc = new SourceLocation("scope.xml", 1, 1);
        NodeIR panelBtn = new NodeIR("Button", "panelBtn", "panelBtn", List.of(),
                Map.of("text", "OK"), Map.of("if", BindingSpec.oneWay("if", "showPanel")),
                Map.of(), null, List.of(), loc);
        NodeIR root = new NodeIR("Screen", "rootScreen", null, List.of(),
                Map.of(), Map.of(), Map.of(), null, List.of(panelBtn), loc);

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.setIdentityGraceFrames(1);
        runtime.mount(root, CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);

        int baselineMountDisposables = runtime.lifecycleScope().activeMountDisposableCount();

        // Attach multiple listeners to panelBtn and manually dispose one before eviction
        KairoId btnId1 = renderer.findByLogicalId("panelBtn").id();
        Disposable manual = runtime.addEventListener(btnId1, CommonEvents.ClickEvent.class, e -> {});
        runtime.addCaptureEventListener(btnId1, CommonEvents.ClickEvent.class, e -> {});
        assertThat(runtime.ownedNodeScopeCount()).isEqualTo(1);
        assertThat(runtime.retainedListenerDisposableCount()).isEqualTo(2);

        manual.dispose();
        assertThat(runtime.listenerCount(btnId1)).isEqualTo(1);
        assertThat(runtime.retainedListenerDisposableCount()).isEqualTo(1);

        // Repeated show/hide churn must bound node scopes and lifecycle mount disposables
        for (int cycle = 0; cycle < 10; cycle++) {
            if (cycle > 0) {
                showPanel.set(true);
                KairoId btnId = renderer.findByLogicalId("panelBtn").id();
                runtime.addEventListener(btnId, CommonEvents.ClickEvent.class, e -> {});
                runtime.addCaptureEventListener(btnId, CommonEvents.ClickEvent.class, e -> {});
                assertThat(runtime.ownedNodeScopeCount()).isEqualTo(1);
            }

            showPanel.set(false);
            runtime.recompute(); // inactiveFrames becomes 2 > grace(1) -> evicted
            assertThat(runtime.ownedNodeScopeCount()).isZero();
            assertThat(runtime.retainedListenerDisposableCount()).isZero();
            assertThat(runtime.lifecycleScope().childScopeCount()).isZero();
            assertThat(runtime.lifecycleScope().activeMountDisposableCount()).isEqualTo(baselineMountDisposables);
        }

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
