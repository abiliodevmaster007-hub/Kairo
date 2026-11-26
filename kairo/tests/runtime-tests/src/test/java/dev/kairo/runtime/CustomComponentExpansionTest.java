package dev.kairo.runtime;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.basic.BasicUI;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.events.CommonEvents;
import dev.kairo.state.State;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class CustomComponentExpansionTest {
    @Test
    void runtimeExpandsCustomComponentWithNamedSlotsDefaultsAndStyles() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        ComponentDescriptor alertBanner = ComponentDescriptor.builder("AlertBanner")
                .role(ComponentDescriptor.SemanticRole.CONTAINER)
                .children(ComponentDescriptor.ChildrenPolicy.SLOTS)
                .slots("header", "default")
                .prop("severity", String.class, "info")
                .build();

        registry.registerCustom(alertBanner, (invocation, slots) ->
                BasicUI.card()
                        .classes("alert-banner", "severity-" + invocation.properties().get("severity"))
                        .child(BasicUI.text(invocation.properties().get("severity").toUpperCase()))
                        .child(BasicUI.slotPlaceholder("header"))
                        .child(BasicUI.slotPlaceholder("default"))
                        .build()
        );

        String xml = """
                <Screen id="root">
                  <AlertBanner id="netAlert" class="urgent" severity="warning">
                    <Text id="hdrText" slot="header" value="Ligação instável"/>
                    <Button id="retryBtn" onClick="retry">Tentar de novo</Button>
                  </AlertBanner>
                </Screen>
                """;
        var parsed = new KairoXmlParser().parse("alert.xml", xml);
        assertThat(parsed.diagnostics().hasErrors()).isFalse();

        CssStylesheet sheet = new KairoCssParser().parse("""
                Card.urgent { background: #7C2D12; }
                """);

        AtomicBoolean retried = new AtomicBoolean(false);
        KairoRuntime.ScreenModel model = new KairoRuntime.ScreenModel() {
            @Override public Map<String, Supplier<Object>> exposedBindings() { return Map.of(); }
            @Override public Map<String, State<String>> twoWayStringStates() { return Map.of(); }
            @Override public Map<String, Runnable> actions() { return Map.of("retry", () -> retried.set(true)); }
        };

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(parsed.root(), sheet, model, Fixtures.STANDARD_PHONE);

        FakeRenderer.FakeNode bannerRoot = renderer.findByLogicalId("netAlert");
        assertThat(bannerRoot).isNotNull();
        assertThat(bannerRoot.componentType()).isEqualTo("Card");
        assertThat(bannerRoot.props).containsEntry("padding", "16"); // Card default prop injected
        assertThat(bannerRoot.style.get("background")).isEqualTo("#7C2D12");

        assertThat(renderer.findByLogicalId("hdrText").props.get("value")).isEqualTo("Ligação instável");
        renderer.dispatch(new CommonEvents.ClickEvent(renderer.findByLogicalId("retryBtn").id()));
        assertThat(retried.get()).isTrue();

        runtime.destroy();
    }
}
