package dev.kairo.runtime;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.events.CommonEvents;
import dev.kairo.state.Derived;
import dev.kairo.state.ObservableList;
import dev.kairo.state.State;
import dev.kairo.style.VisualState;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class ReactiveRuntimeTest {
    @Test
    void runtimeHandlesConditionalRenderingObservableListFocusAndZeroLeakDestroy() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        String xml = """
                <Screen id="home">
                  <Column id="col">
                    <Text id="banner" if="{{ showBanner }}" value="{{ label }}"/>
                    <Text id="count" value="{{ itemCount }}"/>
                    <Button id="action" onClick="toggle">Alternar</Button>
                  </Column>
                </Screen>
                """;
        var parsed = new KairoXmlParser().parse("home.xml", xml);
        assertThat(parsed.diagnostics().hasErrors()).isFalse();

        CssStylesheet sheet = new KairoCssParser().parse("""
                Button { background: #1E293B; }
                Button:focused { background: #4F46E5; }
                """);

        State<Boolean> showBanner = State.of(false);
        State<String> label = State.of("Inicial");
        ObservableList<String> items = ObservableList.of(List.of("Item 1"));
        Derived<Integer> itemCount = Derived.derive(items::size);

        KairoRuntime.ScreenModel model = new KairoRuntime.ScreenModel() {
            @Override
            public Map<String, Supplier<Object>> exposedBindings() {
                return Map.of(
                        "showBanner", () -> showBanner,
                        "label", () -> label,
                        "itemCount", () -> itemCount,
                        "items", () -> items
                );
            }

            @Override
            public Map<String, State<String>> twoWayStringStates() {
                return Map.of();
            }

            @Override
            public Map<String, Runnable> actions() {
                return Map.of("toggle", () -> {
                    showBanner.set(!showBanner.get());
                    label.set("Visível");
                    items.add("Item 2");
                });
            }
        };

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(parsed.root(), sheet, model, Fixtures.STANDARD_PHONE);

        assertThat(renderer.findByLogicalId("banner")).isNull();
        assertThat(renderer.findByLogicalId("count").props.get("value")).isEqualTo("1");
        int initialBatches = runtime.patchHistory().size();

        // Click wraps action in State.transaction -> multiple mutations produce 1 coalesced batch
        FakeRenderer.FakeNode btn = renderer.findByLogicalId("action");
        renderer.dispatch(new CommonEvents.ClickEvent(btn.id()));
        assertThat(runtime.patchHistory().size()).isEqualTo(initialBatches + 1);
        assertThat(renderer.findByLogicalId("banner")).isNotNull();
        assertThat(renderer.findByLogicalId("banner").props.get("value")).isEqualTo("Visível");
        assertThat(renderer.findByLogicalId("count").props.get("value")).isEqualTo("2");

        // Capture event listener can intercept bubbling/default action
        List<String> bubbleLog = new ArrayList<>();
        var col = renderer.findByLogicalId("col");
        runtime.addCaptureEventListener(col.id(), CommonEvents.ClickEvent.class, e -> {
            bubbleLog.add("captured");
            e.preventDefault();
        });
        renderer.dispatch(new CommonEvents.ClickEvent(btn.id()));
        assertThat(bubbleLog).containsExactly("captured");
        assertThat(renderer.findByLogicalId("banner")).isNotNull();

        // FocusEvent activates :focused cascade rule incrementally
        renderer.dispatch(new CommonEvents.FocusEvent(btn.id(), true));
        assertThat(renderer.findByLogicalId("action").style.get("background")).isEqualTo("#4F46E5");
        assertThat(renderer.findByLogicalId("action").visualStates).contains(VisualState.FOCUSED);

        // Destroy cleans up all subscriptions & listeners -> state updates produce 0 batches
        int batchesBeforeDestroy = runtime.patchHistory().size();
        runtime.destroy();
        showBanner.set(false);
        label.set("Depois de destroy");
        assertThat(runtime.patchHistory().size()).isEqualTo(batchesBeforeDestroy);
    }
}
