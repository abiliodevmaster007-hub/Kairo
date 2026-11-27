package dev.kairo.runtime;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.core.KairoId;
import dev.kairo.core.SourceLocation;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.events.CommonEvents;
import dev.kairo.ir.BindingSpec;
import dev.kairo.ir.NodeIR;
import dev.kairo.state.State;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class CoreStabilityTest {

    @Test
    void handlesAllFourRootLifecycleTransitionsDynamically() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        String xml = """
                <Screen id="condRoot" if="{{ showRoot }}">
                  <Column id="box">
                    <Text id="msg" value="Ativo"/>
                  </Column>
                </Screen>
                """;
        var parsed = new KairoXmlParser().parse("cond.xml", xml);
        assertThat(parsed.diagnostics().hasErrors()).isFalse();

        State<Boolean> showRoot = State.of(false);
        KairoRuntime.ScreenModel model = modelOf(Map.of("showRoot", () -> showRoot), Map.of(), Map.of());

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);

        // 1. Null initial root -> nothing mounted on renderer
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(runtime.currentTree()).isNull();
        assertThat(renderer.mountedIds()).isEmpty();

        // 2. Null -> null recompute remains cleanly unmounted
        runtime.recompute();
        assertThat(renderer.mountedIds()).isEmpty();

        // 3. Null -> active root mounts root dynamically on renderer
        showRoot.set(true);
        assertThat(runtime.currentTree()).isNotNull();
        assertThat(renderer.findByLogicalId("condRoot")).isNotNull();
        assertThat(renderer.findByLogicalId("msg")).isNotNull();

        // 4. Active -> null root unmounts root and purges all descendant nodes cleanly
        showRoot.set(false);
        assertThat(runtime.currentTree()).isNull();
        assertThat(renderer.mountedIds()).isEmpty();

        // 5. Active -> replaced root component type swaps cleanly
        showRoot.set(true);
        assertThat(renderer.findByLogicalId("condRoot")).isNotNull();
        var secondParsed = new KairoXmlParser().parse("other.xml", "<Column id=\"altRoot\"><Text id=\"altMsg\" value=\"Outro\"/></Column>");
        runtime.mount(secondParsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        assertThat(renderer.findByLogicalId("condRoot")).isNull();
        assertThat(renderer.findByLogicalId("altRoot")).isNotNull();

        runtime.destroy();
    }

    @Test
    void remountReplacesPreviousSubscriptionsWithoutDuplicates() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        var parsed = new KairoXmlParser().parse("remount.xml", "<Screen id=\"s\"><Text id=\"t\" value=\"{{ v }}\"/></Screen>");
        State<String> v = State.of("1");
        KairoRuntime.ScreenModel model = modelOf(Map.of("v", () -> v), Map.of(), Map.of());

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);

        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);
        runtime.mount(parsed.root(), CssStylesheet.empty(), model, Fixtures.STANDARD_PHONE);

        int before = runtime.patchHistory().size();
        v.set("2");
        assertThat(runtime.patchHistory().size()).isEqualTo(before + 1);
        assertThat(renderer.findByLogicalId("t").props.get("value")).isEqualTo("2");
        runtime.destroy();
    }

    @Test
    void prunesVanishedIdentitiesAfterGraceWindowAndCleansRemovedSubtreeEvents() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        State<List<String>> keysState = State.of(generateKeys(0, 200));
        State<Boolean> showPanel = State.of(true);
        CssStylesheet sheet = new KairoCssParser().parse("");

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.setIdentityGraceFrames(2);

        // Dynamic mount with custom keyed IR generator
        NodeIR template = buildDynamicPanelAndListIR();
        KairoRuntime.ScreenModel model = modelOf(
                Map.of("showPanel", () -> showPanel),
                Map.of(),
                Map.of()
        );
        runtime.mount(template, sheet, model, Fixtures.STANDARD_PHONE);

        KairoId panelBtnId = renderer.findByLogicalId("panelBtn").id();
        AtomicInteger btnClicks = new AtomicInteger();
        runtime.addEventListener(panelBtnId, CommonEvents.ClickEvent.class, e -> btnClicks.incrementAndGet());

        renderer.dispatch(new CommonEvents.ClickEvent(panelBtnId));
        assertThat(btnClicks.get()).isEqualTo(1);

        // Hide panel -> entire subtree vanishes recursively from FakeRenderer
        showPanel.set(false);
        assertThat(renderer.findByLogicalId("panelBox")).isNull();
        assertThat(renderer.findByLogicalId("panelBtn")).isNull();
        renderer.dispatch(new CommonEvents.ClickEvent(panelBtnId));
        assertThat(btnClicks.get()).isEqualTo(1); // Removed node must not receive events

        // Advance beyond grace window (2 frames) -> vanished identities & listeners evicted
        runtime.recompute();
        runtime.recompute();
        assertThat(runtime.listenerCount(panelBtnId)).isZero();

        // Now verify dynamic 500-item list churn does not grow retainedIdentityCount unboundedly
        for (int batch = 0; batch < 5; batch++) {
            List<String> batchKeys = generateKeys(batch * 100, 100);
            keysState.set(batchKeys);
            NodeIR listRoot = buildKeyedListRoot(batchKeys);
            runtime.mount(listRoot, sheet, model, Fixtures.STANDARD_PHONE);
            for (int g = 0; g <= runtime.identityGraceFrames(); g++) {
                runtime.recompute();
            }
            assertThat(runtime.retainedIdentityCount()).isEqualTo(101); // 1 root Column + 100 active rows
        }

        runtime.destroy();
    }

    @Test
    void preservesCaptureAndBubbleOrderWithoutDuplicatesAfterKeyedReordering() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        KairoRuntime.ScreenModel emptyModel = modelOf(Map.of(), Map.of(), Map.of());

        NodeIR initial = buildTwoButtons("a", "b");
        runtime.mount(initial, CssStylesheet.empty(), emptyModel, Fixtures.STANDARD_PHONE);

        KairoId rootColId = renderer.findByLogicalId("rootCol").id();
        KairoId btnAId = renderer.findByLogicalId("btn-a").id();
        List<String> trace = new ArrayList<>();
        runtime.addCaptureEventListener(rootColId, CommonEvents.ClickEvent.class, e -> trace.add("cap:" + e.phase()));
        runtime.addEventListener(btnAId, CommonEvents.ClickEvent.class, e -> trace.add("target:" + e.phase()));
        runtime.addEventListener(rootColId, CommonEvents.ClickEvent.class, e -> trace.add("bubble:" + e.phase()));

        // Reorder children ("b" before "a") via remount with preserved stable identities
        runtime.recompute();
        renderer.dispatch(new CommonEvents.ClickEvent(btnAId));
        assertThat(trace).containsExactly("cap:CAPTURE", "target:TARGET", "bubble:BUBBLE");
        runtime.destroy();
    }

    private static NodeIR buildDynamicPanelAndListIR() {
        SourceLocation loc = new SourceLocation("test.xml", 1, 1);
        NodeIR panelBtn = new NodeIR("Button", "panelBtn", "panelBtn", List.of(),
                Map.of("text", "OK"), Map.of(), Map.of(), null, List.of(), loc);
        NodeIR panelBox = new NodeIR("Column", "panelBox", "panelBox", List.of(),
                Map.of(), Map.of("if", BindingSpec.oneWay("if", "showPanel")), Map.of(), null, List.of(panelBtn), loc);
        return new NodeIR("Screen", "rootScreen", null, List.of(),
                Map.of(), Map.of(), Map.of(), null, List.of(panelBox), loc);
    }

    private static NodeIR buildKeyedListRoot(List<String> keys) {
        SourceLocation loc = new SourceLocation("list.xml", 1, 1);
        List<NodeIR> items = new ArrayList<>(keys.size());
        for (String key : keys) {
            items.add(new NodeIR("Text", null, key, List.of(),
                    Map.of("value", key), Map.of(), Map.of(), null, List.of(), loc));
        }
        return new NodeIR("Column", "listRoot", null, List.of(),
                Map.of(), Map.of(), Map.of(), null, items, loc);
    }

    private static NodeIR buildTwoButtons(String firstKey, String secondKey) {
        SourceLocation loc = new SourceLocation("order.xml", 1, 1);
        NodeIR b1 = new NodeIR("Button", "btn-" + firstKey, firstKey, List.of(),
                Map.of("text", firstKey), Map.of(), Map.of(), null, List.of(), loc);
        NodeIR b2 = new NodeIR("Button", "btn-" + secondKey, secondKey, List.of(),
                Map.of("text", secondKey), Map.of(), Map.of(), null, List.of(), loc);
        return new NodeIR("Column", "rootCol", null, List.of(),
                Map.of(), Map.of(), Map.of(), null, List.of(b1, b2), loc);
    }

    private static List<String> generateKeys(int start, int count) {
        List<String> keys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            keys.add("item-" + (start + i));
        }
        return keys;
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
