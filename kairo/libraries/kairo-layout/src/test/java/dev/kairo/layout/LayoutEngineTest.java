package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class LayoutEngineTest {
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer fixedLeaf(double w, double h) {
        return (node, constraints) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void distributesWeightedFillPaddingAndAdditiveNonCollapsingMargins() {
        KairoId rootId = KairoId.next("col");
        KairoId aId = KairoId.next("a");
        KairoId bId = KairoId.next("b");
        KairoId cId = KairoId.next("c");

        // Column 300x400, padding=20 -> inner 260x360
        // Fixed A: height=40, margin=10 (total main=60)
        // Gap between 3 children: 2 * 8 = 16
        // Remaining for B fill(1) + C fill(2) = 360 - 60 - 16 = 284 -> 94.666 & 189.333
        var a = new LayoutEngine.LayoutNode(aId, "Text", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("height", "40", "margin", "10")), Map.of(), List.of());
        var b = new LayoutEngine.LayoutNode(bId, "Box", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("height", "fill(1)")), Map.of(), List.of());
        var c = new LayoutEngine.LayoutNode(cId, "Box", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("height", "fill(2)")), Map.of(), List.of());

        var root = new LayoutEngine.LayoutNode(rootId, "Column", LayoutEngine.Axis.VERTICAL,
                ComputedStyle.empty(), Map.of("padding", "20", "gap", "8"), List.of(a, b, c));

        LayoutEngine.LayoutResult result = engine.compute(root, LayoutConstraints.fixed(300, 400), fixedLeaf(100, 40));

        LayoutBounds ba = result.boundsByNode().get(aId);
        LayoutBounds bb = result.boundsByNode().get(bId);
        LayoutBounds bc = result.boundsByNode().get(cId);

        assertThat(ba.x()).isEqualTo(30.0); // 20 padding + 10 left margin
        assertThat(ba.y()).isEqualTo(30.0); // 20 padding + 10 top margin
        assertThat(ba.height()).isEqualTo(40.0);
        // Bottom margin of A (10) + gap (8) -> B starts at 30 + 40 + 10 + 8 = 88 (zero margin collapse)
        assertThat(bb.y()).isEqualTo(88.0);
        assertThat(bc.height()).isCloseTo(bb.height() * 2.0, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.overflowByNode().get(rootId)).isFalse();
    }

    @Test
    void supportsWrapFlowRowsAndShrinkWithMinWidth() {
        KairoId rowWrapId = KairoId.next("wrapRow");
        KairoId i1 = KairoId.next("i1");
        KairoId i2 = KairoId.next("i2");
        KairoId i3 = KairoId.next("i3");

        var wrapRow = new LayoutEngine.LayoutNode(rowWrapId, "Row", LayoutEngine.Axis.HORIZONTAL,
                new ComputedStyle(Map.of("wrap", "wrap", "gap", "10", "height", "auto")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(i1, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "120", "height", "30")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i2, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "120", "height", "30")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(i3, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "120", "height", "30")), Map.of(), List.of())
                ));

        LayoutEngine.LayoutResult wrapRes = engine.compute(wrapRow, LayoutConstraints.atMost(260, 400), fixedLeaf(120, 30));
        assertThat(wrapRes.boundsByNode().get(i1).y()).isEqualTo(0.0);
        assertThat(wrapRes.boundsByNode().get(i2).y()).isEqualTo(0.0);
        assertThat(wrapRes.boundsByNode().get(i3).y()).isEqualTo(40.0); // 30 height + 10 gap wrapped onto row 2

        KairoId shrinkRowId = KairoId.next("shrinkRow");
        KairoId s1 = KairoId.next("s1");
        KairoId s2 = KairoId.next("s2");
        var shrinkRow = new LayoutEngine.LayoutNode(shrinkRowId, "Row", LayoutEngine.Axis.HORIZONTAL,
                ComputedStyle.empty(), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(s1, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "150", "min-width", "100", "shrink", "1")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(s2, "Box", LayoutEngine.Axis.VERTICAL, new ComputedStyle(Map.of("width", "150", "shrink", "1")), Map.of(), List.of())
                ));
        LayoutEngine.LayoutResult shrinkRes = engine.compute(shrinkRow, LayoutConstraints.fixed(220, 100), fixedLeaf(150, 40));
        assertThat(shrinkRes.boundsByNode().get(s1).width()).isGreaterThanOrEqualTo(100.0);
        assertThat(shrinkRes.boundsByNode().get(s1).width() + shrinkRes.boundsByNode().get(s2).width()).isCloseTo(220.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    void detectsOverflowInScrollAndClipContainersAndAlignsOverlayChildren() {
        KairoId scrollId = KairoId.next("scroll");
        KairoId tallChild = KairoId.next("tall");
        var scrollNode = new LayoutEngine.LayoutNode(scrollId, "Scroll", LayoutEngine.Axis.VERTICAL,
                ComputedStyle.empty(), Map.of(),
                List.of(new LayoutEngine.LayoutNode(tallChild, "Box", LayoutEngine.Axis.VERTICAL,
                        new ComputedStyle(Map.of("height", "500")), Map.of(), List.of())));

        LayoutEngine.LayoutResult scrollRes = engine.compute(scrollNode, LayoutConstraints.fixed(300, 200), fixedLeaf(100, 40));
        OverflowPolicy.OverflowInfo info = scrollRes.overflowDetails().get(scrollId);
        assertThat(info.overflowY()).isTrue();
        assertThat(info.contentHeight()).isEqualTo(500.0);
        assertThat(info.policy()).isEqualTo(OverflowPolicy.SCROLL);

        KairoId stackId = KairoId.next("stack");
        KairoId centeredId = KairoId.next("centered");
        var stackNode = new LayoutEngine.LayoutNode(stackId, "Stack", LayoutEngine.Axis.OVERLAY,
                new ComputedStyle(Map.of("align", "center")), Map.of(),
                List.of(new LayoutEngine.LayoutNode(centeredId, "Box", LayoutEngine.Axis.VERTICAL,
                        new ComputedStyle(Map.of("width", "100", "height", "40")), Map.of(), List.of())));

        LayoutEngine.LayoutResult stackRes = engine.compute(stackNode, LayoutConstraints.fixed(300, 200), fixedLeaf(100, 40));
        LayoutBounds cb = stackRes.boundsByNode().get(centeredId);
        assertThat(cb.x()).isEqualTo(100.0);
        assertThat(cb.y()).isEqualTo(80.0);
    }
}
