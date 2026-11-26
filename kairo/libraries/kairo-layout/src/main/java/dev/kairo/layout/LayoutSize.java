package dev.kairo.layout;

public sealed interface LayoutSize {
    record Auto() implements LayoutSize {}
    record Fixed(double px) implements LayoutSize {}
    record Fill(double weight) implements LayoutSize {
        public Fill {
            if (weight <= 0) throw new IllegalArgumentException("Fill weight must be positive");
        }
    }
    record Percent(double fraction) implements LayoutSize {
        public Percent {
            if (fraction < 0) throw new IllegalArgumentException("Percent fraction must be non-negative");
        }
    }

    static LayoutSize parse(String raw, LayoutSize fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        String v = raw.trim().toLowerCase();
        if ("auto".equals(v)) return new Auto();
        if ("fill".equals(v)) return new Fill(1.0);
        if (v.startsWith("fill(") && v.endsWith(")")) {
            try {
                double w = Double.parseDouble(v.substring(5, v.length() - 1).trim());
                return new Fill(w);
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
        if (v.endsWith("%")) {
            try {
                double pct = Double.parseDouble(v.substring(0, v.length() - 1).trim());
                return new Percent(pct / 100.0);
            } catch (IllegalArgumentException ex) {
                return fallback;
            }
        }
        String clean = v.replace("px", "").replace("dp", "").trim();
        try {
            return new Fixed(Double.parseDouble(clean));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
