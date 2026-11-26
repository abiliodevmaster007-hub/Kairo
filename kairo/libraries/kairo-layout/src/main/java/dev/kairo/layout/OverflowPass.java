package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pass 3 of Kairo Layout Engine: detects horizontal and vertical content overflow
 * relative to distributed container bounds and attaches OverflowPolicy metadata.
 */
final class OverflowPass {
    record Result(
            Map<KairoId, Boolean> overflowFlags,
            Map<KairoId, OverflowPolicy.OverflowInfo> details
    ) {}

    Result execute(LayoutEngine.LayoutNode root, Map<KairoId, LayoutBounds> boundsByNode) {
        Map<KairoId, Boolean> flags = new LinkedHashMap<>();
        Map<KairoId, OverflowPolicy.OverflowInfo> details = new LinkedHashMap<>();
        inspect(root, boundsByNode, flags, details);
        return new Result(flags, details);
    }

    private void inspect(
            LayoutEngine.LayoutNode node,
            Map<KairoId, LayoutBounds> boundsByNode,
            Map<KairoId, Boolean> flags,
            Map<KairoId, OverflowPolicy.OverflowInfo> details) {
        LayoutBounds self = boundsByNode.getOrDefault(node.id(), LayoutBounds.ZERO);
        OverflowPolicy policy = LayoutStyleReader.overflowPolicy(node);

        if (node.children().isEmpty()) {
            flags.put(node.id(), false);
            details.put(node.id(), new OverflowPolicy.OverflowInfo(false, false, self.width(), self.height(), policy));
            return;
        }

        ComputedStyle.EdgeInsets padding = LayoutStyleReader.padding(node);
        double maxRight = self.x();
        double maxBottom = self.y();
        for (LayoutEngine.LayoutNode child : node.children()) {
            LayoutBounds cb = boundsByNode.getOrDefault(child.id(), LayoutBounds.ZERO);
            ComputedStyle.EdgeInsets cmMargin = LayoutStyleReader.margin(child);
            maxRight = Math.max(maxRight, cb.right() + cmMargin.right());
            maxBottom = Math.max(maxBottom, cb.bottom() + cmMargin.bottom());
            inspect(child, boundsByNode, flags, details);
        }

        double contentW = Math.max(0, (maxRight - self.x()) + padding.right());
        double contentH = Math.max(0, (maxBottom - self.y()) + padding.bottom());
        boolean overflowX = contentW > self.width() + 0.5;
        boolean overflowY = contentH > self.height() + 0.5;

        flags.put(node.id(), overflowX || overflowY);
        details.put(node.id(), new OverflowPolicy.OverflowInfo(overflowX, overflowY, contentW, contentH, policy));
    }
}
