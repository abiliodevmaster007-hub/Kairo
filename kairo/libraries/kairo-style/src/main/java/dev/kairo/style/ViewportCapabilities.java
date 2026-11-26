package dev.kairo.style;

/**
 * Platform-neutral viewport/input capabilities consumed during style resolution.
 */
public record ViewportCapabilities(
        boolean hoverSupported,
        boolean highContrast,
        double densityScale
) {
    public static final ViewportCapabilities DEFAULT = new ViewportCapabilities(false, false, 1.0);

    public ViewportCapabilities {
        if (densityScale <= 0) {
            throw new IllegalArgumentException("densityScale must be positive");
        }
    }

    public static ViewportCapabilities android(double densityScale) {
        return new ViewportCapabilities(false, false, densityScale);
    }

    public static ViewportCapabilities desktopPointer(double densityScale) {
        return new ViewportCapabilities(true, false, densityScale);
    }
}
