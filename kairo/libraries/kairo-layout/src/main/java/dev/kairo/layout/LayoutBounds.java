package dev.kairo.layout;

public record LayoutBounds(double x, double y, double width, double height) {
    public static final LayoutBounds ZERO = new LayoutBounds(0, 0, 0, 0);

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }

    public boolean contains(double px, double py) {
        return px >= x && px <= right() && py >= y && py <= bottom();
    }

    public boolean intersects(LayoutBounds other) {
        return other != null
                && this.x < other.right()
                && this.right() > other.x
                && this.y < other.bottom()
                && this.bottom() > other.y;
    }
}
