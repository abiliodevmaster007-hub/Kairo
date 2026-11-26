package dev.kairo.layout;

import dev.kairo.style.ComputedStyle;

/**
 * Resolves neutral layout properties from ComputedStyle with XML attribute fallbacks.
 */
final class LayoutStyleReader {
    private LayoutStyleReader() {}

    static String raw(LayoutEngine.LayoutNode node, String property) {
        if (node.style() != null) {
            String styled = node.style().get(property);
            if (styled != null && !styled.isBlank()) {
                return styled;
            }
        }
        return node.props() != null ? node.props().get(property) : null;
    }

    static double px(LayoutEngine.LayoutNode node, String property, double fallback) {
        String val = raw(node, property);
        if (val == null || val.isBlank()) return fallback;
        String first = val.trim().split("\\s+")[0]
                .replace("px", "")
                .replace("dp", "")
                .trim();
        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static double number(LayoutEngine.LayoutNode node, String property, double fallback) {
        String val = raw(node, property);
        if (val == null || val.isBlank()) return fallback;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static ComputedStyle.EdgeInsets padding(LayoutEngine.LayoutNode node) {
        return ComputedStyle.EdgeInsets.parse(raw(node, "padding"));
    }

    static ComputedStyle.EdgeInsets margin(LayoutEngine.LayoutNode node) {
        return ComputedStyle.EdgeInsets.parse(raw(node, "margin"));
    }

    static double gap(LayoutEngine.LayoutNode node) {
        return Math.max(0, px(node, "gap", 0));
    }

    static LayoutSize size(LayoutEngine.LayoutNode node, String property, LayoutSize fallback) {
        return LayoutSize.parse(raw(node, property), fallback);
    }

    static double minWidth(LayoutEngine.LayoutNode node) {
        return Math.max(0, px(node, "min-width", 0));
    }

    static double maxWidth(LayoutEngine.LayoutNode node) {
        double v = px(node, "max-width", Double.POSITIVE_INFINITY);
        return v >= 0 ? v : Double.POSITIVE_INFINITY;
    }

    static double minHeight(LayoutEngine.LayoutNode node) {
        return Math.max(0, px(node, "min-height", 0));
    }

    static double maxHeight(LayoutEngine.LayoutNode node) {
        double v = px(node, "max-height", Double.POSITIVE_INFINITY);
        return v >= 0 ? v : Double.POSITIVE_INFINITY;
    }

    static double grow(LayoutEngine.LayoutNode node) {
        return Math.max(0, number(node, "grow", 0));
    }

    static double shrink(LayoutEngine.LayoutNode node) {
        return Math.max(0, number(node, "shrink", 0));
    }

    static boolean wrap(LayoutEngine.LayoutNode node) {
        return "wrap".equalsIgnoreCase(raw(node, "wrap"));
    }

    static LayoutAlignment align(LayoutEngine.LayoutNode node, LayoutAlignment fallback) {
        return LayoutAlignment.parse(raw(node, "align"), fallback);
    }

    static OverflowPolicy overflowPolicy(LayoutEngine.LayoutNode node) {
        OverflowPolicy fallback = "Scroll".equals(node.componentType())
                ? OverflowPolicy.SCROLL
                : OverflowPolicy.VISIBLE;
        return OverflowPolicy.parse(raw(node, "overflow"), fallback);
    }

    static LayoutEngine.Axis axis(LayoutEngine.LayoutNode node) {
        String override = raw(node, "axis");
        if (override != null && !override.isBlank()) {
            return switch (override.trim().toLowerCase()) {
                case "horizontal", "row" -> LayoutEngine.Axis.HORIZONTAL;
                case "overlay", "stack", "box" -> LayoutEngine.Axis.OVERLAY;
                case "vertical", "column" -> LayoutEngine.Axis.VERTICAL;
                default -> node.axis();
            };
        }
        return node.axis();
    }

    static double clampDimension(double value, double minVal, double maxVal) {
        double capped = Double.isFinite(maxVal) ? Math.min(maxVal, value) : value;
        return Math.max(minVal, capped);
    }
}
