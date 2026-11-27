package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class OverflowLayoutMathTest {
    private static final Offset<Double> EPS = Offset.offset(0.01);
    private final LayoutEngine engine = new LayoutEngine();

    private LayoutEngine.LeafMeasurer leaf(double w, double h) {
        return (node, c) -> new LayoutEngine.MeasuredSize(w, h);
    }

    @Test
    void reportsClipVisibleAndScrollOverflowDiagnosticsAccurately() {
        KairoId clipId = KairoId.next("clip");
        KairoId visId = KairoId.next("vis");

        var clipBox = new LayoutEngine.LayoutNode(clipId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("overflow", "clip", "padding", "10")), Map.of(),
                List.of(new LayoutEngine.LayoutNode(KairoId.next("wide"), "Box", LayoutEngine.Axis.VERTICAL,
                        new ComputedStyle(Map.of("width", "340", "height", "50")), Map.of(), List.of())));

        var clipRes = engine.compute(clipBox, LayoutConstraints.fixed(200, 100), leaf(50, 50));
        var clipInfo = clipRes.overflowDetails().get(clipId);
        assertThat(clipRes.overflowByNode().get(clipId)).isTrue();
        assertThat(clipInfo.overflowX()).isTrue();
        assertThat(clipInfo.overflowY()).isFalse();
        assertThat(clipInfo.contentWidth()).isCloseTo(360.0, EPS); // 10 + 340 + 10
        assertThat(clipInfo.policy()).isEqualTo(OverflowPolicy.CLIP);

        var visBox = new LayoutEngine.LayoutNode(visId, "Column", LayoutEngine.Axis.VERTICAL,
                new ComputedStyle(Map.of("overflow", "visible")), Map.of(),
                List.of(new LayoutEngine.LayoutNode(KairoId.next("tall"), "Box", LayoutEngine.Axis.VERTICAL,
                        new ComputedStyle(Map.of("width", "100", "height", "250")), Map.of(), List.of())));

        var visRes = engine.compute(visBox, LayoutConstraints.fixed(200, 100), leaf(50, 50));
        var visInfo = visRes.overflowDetails().get(visId);
        assertThat(visRes.overflowByNode().get(visId)).isTrue();
        assertThat(visInfo.overflowX()).isFalse();
        assertThat(visInfo.overflowY()).isTrue();
        assertThat(visInfo.contentHeight()).isCloseTo(250.0, EPS);
        assertThat(visInfo.policy()).isEqualTo(OverflowPolicy.VISIBLE);
    }

    @Test
    void computesOverlayChildrenWithMarginsAndAlignments() {
        KairoId stackId = KairoId.next("stack");
        KairoId topLeft = KairoId.next("tl");
        KairoId bottomRight = KairoId.next("br");

        var stack = new LayoutEngine.LayoutNode(stackId, "Stack", LayoutEngine.Axis.OVERLAY,
                new ComputedStyle(Map.of("padding", "10")), Map.of(),
                List.of(
                        new LayoutEngine.LayoutNode(topLeft, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "40", "height", "20", "margin", "5", "align", "start")), Map.of(), List.of()),
                        new LayoutEngine.LayoutNode(bottomRight, "Box", LayoutEngine.Axis.VERTICAL,
                                new ComputedStyle(Map.of("width", "60", "height", "30", "margin", "10", "align", "end")), Map.of(), List.of())
                ));

        // Container 200x120, padding=10 -> inner (10,10,180,100)
        // topLeft: margin=5 -> x=15, y=15
        // bottomRight: margin=10 -> avail=(160, 80) -> x = 20 + (160 - 60) = 120, y = 20 + (80 - 30) = 70
        var res = engine.compute(stack, LayoutConstraints.fixed(200, 120), leaf(40, 20));
        assertThat(res.boundsByNode().get(topLeft)).isEqualTo(new LayoutBounds(15, 15, 40, 20));
        assertThat(res.boundsByNode().get(bottomRight)).isEqualTo(new LayoutBounds(120, 70, 60, 30));
        assertThat(res.overflowByNode().get(stackId)).isFalse();
    }
}
