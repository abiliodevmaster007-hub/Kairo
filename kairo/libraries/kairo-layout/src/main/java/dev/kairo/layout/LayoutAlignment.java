package dev.kairo.layout;

public enum LayoutAlignment {
    START,
    CENTER,
    END,
    STRETCH;

    public static LayoutAlignment parse(String raw, LayoutAlignment fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        return switch (raw.trim().toLowerCase()) {
            case "start", "left", "top" -> START;
            case "center", "middle" -> CENTER;
            case "end", "right", "bottom" -> END;
            case "stretch" -> STRETCH;
            default -> fallback;
        };
    }
}
