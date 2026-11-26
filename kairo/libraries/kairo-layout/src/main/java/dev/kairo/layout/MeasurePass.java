package dev.kairo.layout;

import dev.kairo.core.KairoId;
import dev.kairo.style.ComputedStyle;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pass 1 of Kairo Layout Engine: recursive intrinsic measurement with additive padding/margins,
 * min/max clamping, wrap line estimation, Scroll viewport expansion, and leaf-only LeafMeasurer calls.
 */
final class MeasurePass {
    Map<KairoId, LayoutEngine.MeasuredSize> execute(
            LayoutEngine.LayoutNode root,
            LayoutConstraints rootConstraints,
            LayoutEngine.LeafMeasurer leafMeasurer) {
        Map<KairoId, LayoutEngine.MeasuredSize> measured = new LinkedHashMap<>();
        measureNode(root, rootConstraints, leafMeasurer, measured);
        return measured;
    }

    private LayoutEngine.MeasuredSize measureNode(
            LayoutEngine.LayoutNode node,
            LayoutConstraints incoming,
            LayoutEngine.LeafMeasurer leafMeasurer,
            Map<KairoId, LayoutEngine.MeasuredSize> out) {
        double minW = LayoutStyleReader.minWidth(node);
        double maxW = LayoutStyleReader.maxWidth(node);
        double minH = LayoutStyleReader.minHeight(node);
        double maxH = LayoutStyleReader.maxHeight(node);

        LayoutConstraints nodeConstraints = new LayoutConstraints(
                Math.max(incoming.minWidth(), minW),
                Math.min(incoming.maxWidth(), maxW),
                Math.max(incoming.minHeight(), minH),
                Math.min(incoming.maxHeight(), maxH)
        );

        LayoutSize wSpec = LayoutStyleReader.size(node, "width", new LayoutSize.Auto());
        LayoutSize hSpec = LayoutStyleReader.size(node, "height", new LayoutSize.Auto());

        if (node.children().isEmpty()) {
            LayoutEngine.MeasuredSize leaf = leafMeasurer.measureLeaf(node, nodeConstraints);
            double w = resolveMeasuredDimension(wSpec, leaf.width(), incoming.maxWidth(), minW, maxW);
            double h = resolveMeasuredDimension(hSpec, leaf.height(), incoming.maxHeight(), minH, maxH);
            LayoutEngine.MeasuredSize result = new LayoutEngine.MeasuredSize(
                    nodeConstraints.clampWidth(w),
                    nodeConstraints.clampHeight(h)
            );
            out.put(node.id(), result);
            return result;
        }

        ComputedStyle.EdgeInsets padding = LayoutStyleReader.padding(node);
        double gap = LayoutStyleReader.gap(node);
        LayoutEngine.Axis axis = LayoutStyleReader.axis(node);
        boolean isScroll = "Scroll".equals(node.componentType())
                || LayoutStyleReader.overflowPolicy(node) == OverflowPolicy.SCROLL;

        double innerMaxW = Double.isFinite(nodeConstraints.maxWidth())
                ? Math.max(0, nodeConstraints.maxWidth() - padding.horizontal())
                : Double.POSITIVE_INFINITY;
        double innerMaxH = Double.isFinite(nodeConstraints.maxHeight())
                ? Math.max(0, nodeConstraints.maxHeight() - padding.vertical())
                : Double.POSITIVE_INFINITY;

        if (isScroll && axis == LayoutEngine.Axis.VERTICAL) {
            innerMaxH = Double.POSITIVE_INFINITY;
        } else if (isScroll && axis == LayoutEngine.Axis.HORIZONTAL) {
            innerMaxW = Double.POSITIVE_INFINITY;
        }

        LayoutConstraints baseInner = LayoutConstraints.atMost(innerMaxW, innerMaxH);
        boolean wrap = LayoutStyleReader.wrap(node);

        double contentW = 0;
        double contentH = 0;

        if (axis == LayoutEngine.Axis.OVERLAY) {
            for (LayoutEngine.LayoutNode child : node.children()) {
                ComputedStyle.EdgeInsets cmMargin = LayoutStyleReader.margin(child);
                LayoutEngine.MeasuredSize cs = measureNode(child, baseInner.shrinkBy(cmMargin), leafMeasurer, out);
                contentW = Math.max(contentW, cs.width() + cmMargin.horizontal());
                contentH = Math.max(contentH, cs.height() + cmMargin.vertical());
            }
        } else if (axis == LayoutEngine.Axis.HORIZONTAL) {
            double lineW = 0;
            double lineH = 0;
            int lineItems = 0;
            for (LayoutEngine.LayoutNode child : node.children()) {
                ComputedStyle.EdgeInsets cmMargin = LayoutStyleReader.margin(child);
                LayoutEngine.MeasuredSize cs = measureNode(child, baseInner.shrinkBy(cmMargin), leafMeasurer, out);
                double cw = cs.width() + cmMargin.horizontal();
                double ch = cs.height() + cmMargin.vertical();
                double nextLineW = lineItems == 0 ? cw : lineW + gap + cw;
                if (wrap && Double.isFinite(innerMaxW) && lineItems > 0 && nextLineW > innerMaxW + 0.5) {
                    contentW = Math.max(contentW, lineW);
                    contentH += lineH + gap;
                    lineW = cw;
                    lineH = ch;
                    lineItems = 1;
                } else {
                    lineW = nextLineW;
                    lineH = Math.max(lineH, ch);
                    lineItems++;
                }
            }
            contentW = Math.max(contentW, lineW);
            contentH += lineH;
        } else {
            double colW = 0;
            double colH = 0;
            int colItems = 0;
            for (LayoutEngine.LayoutNode child : node.children()) {
                ComputedStyle.EdgeInsets cmMargin = LayoutStyleReader.margin(child);
                LayoutEngine.MeasuredSize cs = measureNode(child, baseInner.shrinkBy(cmMargin), leafMeasurer, out);
                double cw = cs.width() + cmMargin.horizontal();
                double ch = cs.height() + cmMargin.vertical();
                double nextColH = colItems == 0 ? ch : colH + gap + ch;
                if (wrap && Double.isFinite(innerMaxH) && colItems > 0 && nextColH > innerMaxH + 0.5) {
                    contentH = Math.max(contentH, colH);
                    contentW += colW + gap;
                    colH = ch;
                    colW = cw;
                    colItems = 1;
                } else {
                    colH = nextColH;
                    colW = Math.max(colW, cw);
                    colItems++;
                }
            }
            contentH = Math.max(contentH, colH);
            contentW += colW;
        }

        double rawW = contentW + padding.horizontal();
        double rawH = contentH + padding.vertical();
        double resolvedW = resolveMeasuredDimension(wSpec, rawW, incoming.maxWidth(), minW, maxW);
        double resolvedH = resolveMeasuredDimension(hSpec, rawH, incoming.maxHeight(), minH, maxH);

        LayoutEngine.MeasuredSize result = new LayoutEngine.MeasuredSize(
                nodeConstraints.clampWidth(resolvedW),
                nodeConstraints.clampHeight(resolvedH)
        );
        out.put(node.id(), result);
        return result;
    }

    private static double resolveMeasuredDimension(
            LayoutSize spec,
            double intrinsic,
            double available,
            double minVal,
            double maxVal) {
        double v;
        if (spec instanceof LayoutSize.Fixed fixed) {
            v = fixed.px();
        } else if (spec instanceof LayoutSize.Percent pct && Double.isFinite(available)) {
            v = available * pct.fraction();
        } else {
            v = intrinsic;
        }
        return LayoutStyleReader.clampDimension(v, minVal, maxVal);
    }
}
