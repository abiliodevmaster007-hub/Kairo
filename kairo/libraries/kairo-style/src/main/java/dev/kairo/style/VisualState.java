package dev.kairo.style;

/**
 * Built-in UI visual states supported by Kairo's native style selector dialect.
 * HOVER is gated by {@link ViewportCapabilities#hoverSupported()} so touch targets ignore hover rules.
 */
public enum VisualState {
    PRESSED,
    FOCUSED,
    DISABLED,
    SELECTED,
    CHECKED,
    ERROR,
    HOVER
}
