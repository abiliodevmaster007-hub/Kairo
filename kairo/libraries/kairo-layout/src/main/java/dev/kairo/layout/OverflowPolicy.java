package dev.kairo.layout;

public enum OverflowPolicy {
    VISIBLE,
    CLIP,
    SCROLL;

    public record OverflowInfo(
            boolean overflowX,
            boolean overflowY,
            double contentWidth,
            double contentHeight,
            OverflowPolicy policy
    ) {
        public boolean hasOverflow() {
            return overflowX || overflowY;
        }
    }

    public static OverflowPolicy parse(String raw, OverflowPolicy fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return switch (raw.trim().toLowerCase()) {
            case "visible" -> VISIBLE;
            case "clip", "hidden" -> CLIP;
            case "scroll", "auto" -> SCROLL;
            default -> fallback;
        };
    }
}
