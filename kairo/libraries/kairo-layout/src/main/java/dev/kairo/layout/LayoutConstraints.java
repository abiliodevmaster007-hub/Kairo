package dev.kairo.layout;

import dev.kairo.style.ComputedStyle;

public record LayoutConstraints(double minWidth, double maxWidth, double minHeight, double maxHeight) {
    public static LayoutConstraints unbounded() {
        return new LayoutConstraints(0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY);
    }

    public static LayoutConstraints fixed(double width, double height) {
        return new LayoutConstraints(width, width, height, height);
    }

    public static LayoutConstraints atMost(double maxWidth, double maxHeight) {
        return new LayoutConstraints(0, maxWidth, 0, maxHeight);
    }

    public double clampWidth(double width) {
        double capped = Double.isFinite(maxWidth) ? Math.min(maxWidth, width) : width;
        return Math.max(minWidth, capped);
    }

    public double clampHeight(double height) {
        double capped = Double.isFinite(maxHeight) ? Math.min(maxHeight, height) : height;
        return Math.max(minHeight, capped);
    }

    public LayoutConstraints shrinkBy(ComputedStyle.EdgeInsets insets) {
        if (insets == null) return this;
        double h = insets.horizontal();
        double v = insets.vertical();
        double nextMinW = Math.max(0, minWidth - h);
        double nextMaxW = Double.isFinite(maxWidth) ? Math.max(nextMinW, maxWidth - h) : Double.POSITIVE_INFINITY;
        double nextMinH = Math.max(0, minHeight - v);
        double nextMaxH = Double.isFinite(maxHeight) ? Math.max(nextMinH, maxHeight - v) : Double.POSITIVE_INFINITY;
        return new LayoutConstraints(nextMinW, nextMaxW, nextMinH, nextMaxH);
    }
}
