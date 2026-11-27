package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class ConstraintsLayoutMathTest {
    private static final Offset<Double> EPS = Offset.offset(0.01);
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer leaf(double w, double h) {
        return (node, c) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void enforcesMinAndMaxWidthAndHeightOnAutoAndFixedNodes() {
        KairoId colId = KairoId.next("col");
        KairoId minW = KairoId.next("minW");
        KairoId maxW = KairoId.next("maxW");
        KairoId minH = KairoId.next("minH");
        KairoId maxH = KairoId.next("maxH");
        KairoId conflict = KairoId.next("conflict");

        var col = new LayoutEngine.LayoutNode(colId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("align", "start")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(minW, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "30", "min-width", "80", "height", "20")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(maxW, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "250", "max-width", "160", "height", "20")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(minH, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "50", "height", "10", "min-height", "45")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(maxH, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "50", "height", "90", "max-height", "55")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(conflict, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "50", "min-width", "120", "max-width", "80", "height", "20")), Map.of(), List.of())
                ));

        var res = engine.compute(col, LayoutConstraints.fixed(300, 400), leaf(40, 20));
        assertThat(res.boundsByNode().get(minW).width()).isCloseTo(80.0, EPS);
        assertThat(res.boundsByNode().get(maxW).width()).isCloseTo(160.0, EPS);
        assertThat(res.boundsByNode().get(minH).height()).isCloseTo(45.0, EPS);
        assertThat(res.boundsByNode().get(maxH).height()).isCloseTo(55.0, EPS);
        assertThat(res.boundsByNode().get(conflict).width()).isCloseTo(120.0, EPS);
    }

    @Test
    void redistributesRemainingSpaceWhenFillSiblingHitsMaxWidth() {
        KairoId rowId = KairoId.next("row");
        KairoId a = KairoId.next("a");
        KairoId b = KairoId.next("b");
        KairoId c = KairoId.next("c");

        // Parent width=300, gap=10 -> total gaps=20. A=100. Remaining=180.
        // B(fill 1) caps at max-width=40, freeing 140 completely for C(fill 2).
        var row = new LayoutEngine.LayoutNode(rowId, "Row", LayoutEngine.Axis.HORIZONTAL,
                new ComputedStyle(Map.of("gap", "10")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(a, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "100", "height", "30")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(b, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "fill(1)", "max-width", "40", "height", "30")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(c, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "fill(2)", "height", "30")), Map.of(), List.of())
                ));

        var res = engine.compute(row, LayoutConstraints.fixed(300, 100), leaf(20, 30));
        assertThat(res.boundsByNode().get(b).width()).isCloseTo(40.0, EPS);
        assertThat(res.boundsByNode().get(c).width()).isCloseTo(140.0, EPS);
        assertThat(res.boundsByNode().get(c).right()).isCloseTo(300.0, EPS);
    }
}
