package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class VerticalLayoutMathTest {
    private static final Offset<Double> EPS = Offset.offset(0.01);
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer leaf(double w, double h) {
        return (node, c) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void computesFixedHeightsGapPaddingAndStretchWidth() {
        KairoId rootId = KairoId.next("col");
        KairoId c1 = KairoId.next("c1");
        KairoId c2 = KairoId.next("c2");
        KairoId c3 = KairoId.next("c3");

        // Parent 300x400, padding=10 -> inner 280x380 starting at (10,10), gap=12
        var root = new LayoutEngine.LayoutNode(rootId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("padding", "10", "gap", "12")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(c1, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("height", "40")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(c2, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("height", "60")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(c3, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("height", "80")), Map.of(), List.of())
                ));

        var res = engine.compute(root, LayoutConstraints.fixed(300, 400), leaf(50, 20));
        assertThat(res.boundsByNode().get(c1)).isEqualTo(new LayoutBounds(10, 10, 280, 40));
        assertThat(res.boundsByNode().get(c2)).isEqualTo(new LayoutBounds(10, 62, 280, 60));
        assertThat(res.boundsByNode().get(c3)).isEqualTo(new LayoutBounds(10, 134, 280, 80));
        assertThat(res.overflowByNode().get(rootId)).isFalse();
    }

    @Test
    void distributesWeightedFillAndGrowProportionally() {
        KairoId rootId = KairoId.next("col");
        KairoId a = KairoId.next("a");
        KairoId b = KairoId.next("b");
        KairoId c = KairoId.next("c");

        // innerH=360, gap=10 (total gaps=20), fixed A=100 -> remaining=240 split 1:2 -> 80 and 160
        var root = new LayoutEngine.LayoutNode(rootId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("gap", "10")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(a, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("height", "100")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(b, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("height", "fill(1)")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(c, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("grow", "2")), Map.of(), List.of())
                ));

        var res = engine.compute(root, LayoutConstraints.fixed(200, 360), leaf(40, 20));
        assertThat(res.boundsByNode().get(a).height()).isCloseTo(100.0, EPS);
        assertThat(res.boundsByNode().get(b).y()).isCloseTo(110.0, EPS);
        assertThat(res.boundsByNode().get(b).height()).isCloseTo(80.0, EPS);
        assertThat(res.boundsByNode().get(c).y()).isCloseTo(200.0, EPS);
        assertThat(res.boundsByNode().get(c).height()).isCloseTo(160.0, EPS);
    }

    @Test
    void redistributesShrinkWhenItemHitsMinHeight() {
        KairoId rootId = KairoId.next("col");
        KairoId s1 = KairoId.next("s1");
        KairoId s2 = KairoId.next("s2");

        // Total=300 in 200px height -> budget=100.
        // s1 shrinks aggressively (shrink=10) but freezes at min-height=130 (reduces by 20).
        // Remaining 80 shrink budget transfers completely to s2 -> 150 - 80 = 70.
        var root = new LayoutEngine.LayoutNode(rootId, "Column", LayoutEngine.Axis.VERTICAL,
                ComputedStyle.empty(), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(s1, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("height", "150", "min-height", "130", "shrink", "10")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(s2, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("height", "150", "min-height", "40", "shrink", "1")), Map.of(), List.of())
                ));

        var res = engine.compute(root, LayoutConstraints.fixed(200, 200), leaf(50, 50));
        assertThat(res.boundsByNode().get(s1).height()).isCloseTo(130.0, EPS);
        assertThat(res.boundsByNode().get(s2).height()).isCloseTo(70.0, EPS);
        assertThat(res.boundsByNode().get(s2).y()).isCloseTo(130.0, EPS);
    }
}
