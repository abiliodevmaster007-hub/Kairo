package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Platform-independent Kairo Layout Engine orchestrating:
 * 1) MeasurePass -> 2) DistributePass -> 3) OverflowPass.
 * Android Renderer only supplies LeafMeasurer and applies the computed LayoutBounds.
 */
public final class LayoutEngine {
    public enum Axis { VERTICAL, HORIZONTAL, OVERLAY }

    public interface LeafMeasurer {
        MeasuredSize measureLeaf(LayoutNode node, LayoutConstraints constraints);
    }

    public record MeasuredSize(double width, double height) {}

    public record LayoutNode(
            KairoId id,
            String componentType,
            Axis axis,
            ComputedStyle style,
            Map<String, String> props,
            List<LayoutNode> children
    ) {}

    public record LayoutResult(
            Map<KairoId, LayoutBounds> boundsByNode,
            Map<KairoId, Boolean> overflowByNode,
            Map<KairoId, OverflowPolicy.OverflowInfo> overflowDetails,
            Map<KairoId, MeasuredSize> measuredSizes
    ) {
        public LayoutResult(
                Map<KairoId, LayoutBounds> boundsByNode,
                Map<KairoId, Boolean> overflowByNode) {
            this(boundsByNode, overflowByNode, Map.of(), Map.of());
        }
    }

    private final MeasurePass measurePass = new MeasurePass();
    private final DistributePass distributePass = new DistributePass();
    private final OverflowPass overflowPass = new OverflowPass();

    public LayoutResult compute(LayoutNode root, LayoutConstraints rootConstraints, LeafMeasurer leafMeasurer) {
        Map<KairoId, MeasuredSize> measured = measurePass.execute(root, rootConstraints, leafMeasurer);

        MeasuredSize rootMeasured = measured.get(root.id());
        LayoutSize rootWSpec = LayoutStyleReader.size(root, "width", new LayoutSize.Fill(1));
        LayoutSize rootHSpec = LayoutStyleReader.size(root, "height", new LayoutSize.Fill(1));
        double rootW = rootConstraints.clampWidth(resolveRootDimension(rootWSpec, rootMeasured.width(), rootConstraints.maxWidth()));
        double rootH = rootConstraints.clampHeight(resolveRootDimension(rootHSpec, rootMeasured.height(), rootConstraints.maxHeight()));

        Map<KairoId, LayoutBounds> bounds = distributePass.execute(root, new LayoutBounds(0, 0, rootW, rootH), measured);
        OverflowPass.Result overflowResult = overflowPass.execute(root, bounds);

        return new LayoutResult(
                Collections.unmodifiableMap(bounds),
                Collections.unmodifiableMap(overflowResult.overflowFlags()),
                Collections.unmodifiableMap(overflowResult.details()),
                Collections.unmodifiableMap(measured)
        );
    }

    private static double resolveRootDimension(LayoutSize spec, double intrinsic, double available) {
        if (spec instanceof LayoutSize.Fixed fixed) return fixed.px();
        if (spec instanceof LayoutSize.Percent pct && Double.isFinite(available)) return available * pct.fraction();
        if (spec instanceof LayoutSize.Fill) return Double.isFinite(available) ? available : intrinsic;
        return intrinsic;
    }
}
