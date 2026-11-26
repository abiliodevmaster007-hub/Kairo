package dev.kairo.style;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Computed style resolved via selector specificity, cascade, token substitution, and typography inheritance.
 * Explicitly avoids web CSS margin collapsing and box-sizing quirks.
 */
public final class ComputedStyle {
    /**
     * Native symmetric/asymmetric box spacing (no CSS margin collapsing).
     */
    public record EdgeInsets(double top, double right, double bottom, double left) {
        public static final EdgeInsets ZERO = new EdgeInsets(0, 0, 0, 0);

        public static EdgeInsets all(double value) {
            return new EdgeInsets(value, value, value, value);
        }

        public static EdgeInsets parse(String raw) {
            if (raw == null || raw.isBlank()) return ZERO;
            String[] tokens = raw.trim().split("\\s+");
            if (tokens.length == 1) {
                double v = parsePxToken(tokens[0]);
                return all(v);
            }
            if (tokens.length == 2) {
                double v = parsePxToken(tokens[0]);
                double h = parsePxToken(tokens[1]);
                return new EdgeInsets(v, h, v, h);
            }
            if (tokens.length >= 4) {
                return new EdgeInsets(
                        parsePxToken(tokens[0]),
                        parsePxToken(tokens[1]),
                        parsePxToken(tokens[2]),
                        parsePxToken(tokens[3])
                );
            }
            return new EdgeInsets(
                    parsePxToken(tokens[0]),
                    parsePxToken(tokens[1]),
                    parsePxToken(tokens[2]),
                    parsePxToken(tokens[1])
            );
        }

        public double horizontal() {
            return left + right;
        }

        public double vertical() {
            return top + bottom;
        }
    }

    private static final Set<String> INHERITED_PROPERTIES = Set.of(
            "color", "font-size", "font-weight", "font-family", "text-align", "line-height"
    );

    private final Map<String, String> values;

    public ComputedStyle(Map<String, String> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static ComputedStyle empty() {
        return new ComputedStyle(Map.of());
    }

    public String get(String property) {
        return values.get(property);
    }

    public String getOrDefault(String property, String fallback) {
        return values.getOrDefault(property, fallback);
    }

    public double getPx(String property, double fallback) {
        String raw = values.get(property);
        if (raw == null || raw.isBlank()) return fallback;
        String firstToken = raw.trim().split("\\s+")[0];
        try {
            return parsePxTokenStrict(firstToken);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public double getDouble(String property, double fallback) {
        String raw = values.get(property);
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public EdgeInsets getInsets(String property) {
        return EdgeInsets.parse(values.get(property));
    }

    public String getColor(String property) {
        if ("background".equals(property) && !values.containsKey("background")) {
            return values.get("background-color");
        }
        return values.get(property);
    }

    public Map<String, String> asMap() {
        return values;
    }

    public static boolean isInherited(String property) {
        return INHERITED_PROPERTIES.contains(property);
    }

    private static double parsePxToken(String token) {
        try {
            return parsePxTokenStrict(token);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static double parsePxTokenStrict(String token) {
        String clean = token.replace("px", "").replace("dp", "").trim();
        return Double.parseDouble(clean);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ComputedStyle other)) return false;
        return values.equals(other.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(values);
    }

    @Override
    public String toString() {
        return "ComputedStyle" + values;
    }
}
