package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class HorizontalLayoutMathTest {
    private static final Offset<Double> EPS = Offset.offset(0.01);
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer leaf(double w, double h) {
        return (node, c) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void computesExactHorizontalFillWeightsWithFixedAndGap() {
        KairoId rowId = KairoId.next("row");
        KairoId a = KairoId.next("a");
        KairoId b = KairoId.next("b");
        KairoId c = KairoId.next("c");

        // Parent width=300, gap=10 -> total gap=20. A=100, remaining=180 -> B(fill 1)=60, C(fill 2)=120.
        var row = new LayoutEngine.LayoutNode(rowId, "Row", LayoutEngine.Axis.HORIZONTAL,
                new ComputedStyle(Map.of("gap", "10")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(a, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "100", "height", "40")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(b, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "fill(1)", "height", "40")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(c, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "fill(2)", "height", "40")), Map.of(), List.of())
                ));

        var res = engine.compute(row, LayoutConstraints.fixed(300, 100), leaf(50, 40));
        LayoutBounds ba = res.boundsByNode().get(a);
        LayoutBounds bb = res.boundsByNode().get(b);
        LayoutBounds bc = res.boundsByNode().get(c);

        assertThat(ba.x()).isCloseTo(0.0, EPS);
        assertThat(ba.width()).isCloseTo(100.0, EPS);
        assertThat(bb.x()).isCloseTo(110.0, EPS);
        assertThat(bb.width()).isCloseTo(60.0, EPS);
        assertThat(bc.x()).isCloseTo(180.0, EPS);
        assertThat(bc.width()).isCloseTo(120.0, EPS);
        assertThat(bc.right()).isCloseTo(300.0, EPS);
    }

    @Test
    void alignsCrossAxisStartCenterEndAndStretch() {
        KairoId rowId = KairoId.next("row");
        KairoId s = KairoId.next("start");
        KairoId ctr = KairoId.next("center");
        KairoId end = KairoId.next("end");
        KairoId str = KairoId.next("stretch");

        var row = new LayoutEngine.LayoutNode(rowId, "Row", LayoutEngine.Axis.HORIZONTAL,
                new ComputedStyle(Map.of("align", "start")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(s, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40", "align", "start")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(ctr, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40", "align", "center")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(end, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "40", "align", "end")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(str, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "50", "height", "fill")), Map.of(), List.of())
                ));

        var res = engine.compute(row, LayoutConstraints.fixed(300, 120), leaf(50, 40));
        assertThat(res.boundsByNode().get(s).y()).isCloseTo(0.0, EPS);
        assertThat(res.boundsByNode().get(ctr).y()).isCloseTo(40.0, EPS);
        assertThat(res.boundsByNode().get(end).y()).isCloseTo(80.0, EPS);
        assertThat(res.boundsByNode().get(str).y()).isCloseTo(0.0, EPS);
        assertThat(res.boundsByNode().get(str).height()).isCloseTo(120.0, EPS);
    }
}
