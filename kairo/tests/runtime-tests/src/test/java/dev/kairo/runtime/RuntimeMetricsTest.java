package dev.kairo.runtime;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.inspector.TreeInspector;
import dev.kairo.state.State;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class RuntimeMetricsTest {

    @Test
    void tracksPerPhaseTimingsIncrementalPatchCountsAndInspectorPerformanceReport() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);

        String xml = """
                <Screen id="metricsScreen">
                  <Column id="rootCol" padding="16" gap="8">
                    <Text id="headline" value="{{ title }}"/>
                    <Text id="optionalBadge" if="{{ showBadge }}" value="Novo"/>
                  </Column>
                </Screen>
                """;
        var parsed = new KairoXmlParser().parse("metrics.xml", xml);
        assertThat(parsed.diagnostics().hasErrors()).isFalse();

        CssStylesheet sheet = new KairoCssParser().parse("""
                Screen { background: #0F172A; }
                Text { color: #F8FAFC; }
                """);

        State<String> title = State.of("Inicial");
        State<Boolean> showBadge = State.of(true);

        KairoRuntime.ScreenModel model = new KairoRuntime.ScreenModel() {
            @Override
            public Map<String, Supplier<Object>> exposedBindings() {
                return Map.of("title", () -> title, "showBadge", () -> showBadge);
            }

            @Override
            public Map<String, State<String>> twoWayStringStates() {
                return Map.of();
            }

            @Override
            public Map<String, Runnable> actions() {
                return Map.of();
            }
        };

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        runtime.mount(parsed.root(), sheet, model, Fixtures.STANDARD_PHONE);

        // Initial mount: Screen + Column + 2 Texts = 4 nodes created
        RuntimeMetrics mountMetrics = runtime.lastMetrics();
        assertThat(mountMetrics.nodesRebuilt()).isEqualTo(4);
        assertThat(mountMetrics.layoutNodesProcessed()).isEqualTo(4);
        assertThat(mountMetrics.nodesCreated()).isEqualTo(4);
        assertThat(mountMetrics.nodesChanged()).isZero();
        assertThat(mountMetrics.nodesRemoved()).isZero();
        assertThat(mountMetrics.totalNanos()).isPositive();

        // Incremental text prop update: 0 created, 0 removed, 1 node changed (#headline)
        title.set("Atualizado");
        RuntimeMetrics updateMetrics = runtime.lastMetrics();
        assertThat(updateMetrics.nodesRebuilt()).isEqualTo(4);
        assertThat(updateMetrics.nodesCreated()).isZero();
        assertThat(updateMetrics.nodesChanged()).isEqualTo(1);
        assertThat(updateMetrics.nodesRemoved()).isZero();
        assertThat(updateMetrics.patchesEmitted()).isPositive();

        // Conditional removal: optionalBadge disappears -> 3 nodes rebuilt, 1 removed
        showBadge.set(false);
        RuntimeMetrics removeMetrics = runtime.lastMetrics();
        assertThat(removeMetrics.nodesRebuilt()).isEqualTo(3);
        assertThat(removeMetrics.nodesRemoved()).isEqualTo(1);
        assertThat(runtime.metricsHistory()).hasSize(3);

        // TreeInspector report surfaces metrics cleanly
        TreeInspector inspector = new TreeInspector();
        String report = inspector.dumpPerformanceReport(runtime);
        assertThat(report).contains("Runtime Performance:");
        assertThat(report).contains("Tree construction:");
        assertThat(report).contains("Style resolution:");
        assertThat(report).contains("Layout:");
        assertThat(report).contains("Reconciliation:");
        assertThat(report).contains("Patch application:");
        assertThat(report).contains("Nodes rebuilt:          3");
        assertThat(report).contains("Nodes removed:          1");

        String snapshot = inspector.dumpSnapshot(runtime, model, renderer);
        assertThat(snapshot).contains("Runtime Performance:");
        assertThat(snapshot).contains("headline");

        runtime.destroy();
        assertThat(runtime.lastMetrics()).isEqualTo(RuntimeMetrics.ZERO);
    }
}
