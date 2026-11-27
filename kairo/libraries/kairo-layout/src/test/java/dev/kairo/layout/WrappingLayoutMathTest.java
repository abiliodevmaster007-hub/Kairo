package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class WrappingLayoutMathTest {
    private static final Offset<Double> EPS = Offset.offset(0.01);
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer leaf(double w, double h) {
        return (node, c) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void wrapsHorizontalItemsAndCentersShorterItemsWithinLineCrossAxis() {
        KairoId rowId = KairoId.next("wrapRow");
        KairoId i1 = KairoId.next("i1");
        KairoId i2 = KairoId.next("i2");
        KairoId i3 = KairoId.next("i3");

        // Available width=260, padding=10 -> innerMaxW=240, gap=10.
        // Line 1: i1(110x50) + 10 + i2(110x30) = 230 <= 240; line cross max = 50 -> i2 centered at y = 10 + (50-30)/2 = 20.
        // Line 2: i3(110x30) starts at x=10, y=10 + 50 + 10 = 70.
        var row = new LayoutEngine.LayoutNode(rowId, "Row", LayoutEngine.Axis.HORIZONTAL,
                new ComputedStyle(Map.of("wrap", "wrap", "padding", "10", "gap", "10", "align", "center", "height", "auto")),
                Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(i1, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "110", "height", "50")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i2, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "110", "height", "30")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i3, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "110", "height", "30")), Map.of(), List.of())
                ));

        var res = engine.compute(row, LayoutConstraints.atMost(260, 400), leaf(110, 30));
        assertThat(res.boundsByNode().get(i1)).isEqualTo(new LayoutBounds(10, 10, 110, 50));
        assertThat(res.boundsByNode().get(i2).x()).isCloseTo(130.0, EPS);
        assertThat(res.boundsByNode().get(i2).y()).isCloseTo(20.0, EPS);
        assertThat(res.boundsByNode().get(i3).x()).isCloseTo(10.0, EPS);
        assertThat(res.boundsByNode().get(i3).y()).isCloseTo(70.0, EPS);
        assertThat(res.boundsByNode().get(rowId).height()).isCloseTo(110.0, EPS); // 10 + 50 + 10 + 30 + 10
    }

    @Test
    void wrapsVerticalColumnsIntoMultipleColumns() {
        KairoId colId = KairoId.next("wrapCol");
        KairoId i1 = KairoId.next("i1");
        KairoId i2 = KairoId.next("i2");
        KairoId i3 = KairoId.next("i3");

        // Height=100, gap=10 -> col 1 fits 40 + 10 + 40 = 90; i3 wraps to col 2 at x = 50 + 10 = 60, y = 0.
        var col = new LayoutEngine.LayoutNode(colId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("wrap", "wrap", "gap", "10", "width", "auto")),
                Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(i1, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i2, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i3, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40")), Map.of(), List.of())
                ));

        var res = engine.compute(col, LayoutConstraints.atMost(300, 100), leaf(50, 40));
        assertThat(res.boundsByNode().get(i1)).isEqualTo(new LayoutBounds(0, 0, 50, 40));
        assertThat(res.boundsByNode().get(i2)).isEqualTo(new LayoutBounds(0, 50, 50, 40));
        assertThat(res.boundsByNode().get(i3)).isEqualTo(new LayoutBounds(60, 0, 50, 40));
        assertThat(res.boundsByNode().get(colId).width()).isCloseTo(110.0, EPS);
    }
}
