package dev.kairo.renderer.android;

import java.util.Locale;

/**
 * Pure ARGB (0xAARRGGBB) color parser for Kairo styles without requiring Android runtime stubs in unit tests.
 */
public final class KairoColorParser {
    private KairoColorParser() {}

    public static int parseArgb(String raw, int fallbackArgb) {
        if (raw == null || raw.isBlank()) {
            return fallbackArgb;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "transparent":
                return 0x00000000;
            case "black":
                return 0xFF000000;
            case "white":
                return 0xFFFFFFFF;
            case "red":
                return 0xFFEF4444;
            default:
                break;
        }
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            try {
                if (hex.length() == 3) {
                    int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                    int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                    int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                    return (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
                if (hex.length() == 6) {
                    int rgb = Integer.parseInt(hex, 16);
                    return 0xFF000000 | rgb;
                }
                if (hex.length() == 8) {
                    return (int) Long.parseLong(hex, 16);
                }
            } catch (NumberFormatException ignored) {
                return fallbackArgb;
            }
        }
        return fallbackArgb;
    }

    public static boolean hasVisibleAlpha(int argb) {
        return ((argb >>> 24) & 0xFF) > 0;
    }
}
