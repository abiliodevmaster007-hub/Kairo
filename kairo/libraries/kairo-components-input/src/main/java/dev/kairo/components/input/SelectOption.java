package dev.kairo.components.input;

import java.util.ArrayList;
import java.util.List;

/**
 * Neutral option descriptor for Select controls without JSON library overhead.
 */
public record SelectOption(String value, String label, boolean disabled) {
    public SelectOption(String value, String label) {
        this(value, label, false);
    }

    public static String serialize(List<SelectOption> options) {
        if (options == null || options.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            SelectOption opt = options.get(i);
            if (i > 0) sb.append('|');
            sb.append(escape(opt.value())).append(':').append(escape(opt.label()));
            if (opt.disabled()) sb.append(":disabled");
        }
        return sb.toString();
    }

    public static List<SelectOption> parse(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<SelectOption> out = new ArrayList<>();
        for (String item : raw.split("\\|")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(":", 3);
            String val = parts[0].trim();
            String label = parts.length > 1 ? parts[1].trim() : val;
            boolean disabled = parts.length > 2 && "disabled".equalsIgnoreCase(parts[2].trim());
            out.add(new SelectOption(val, label, disabled));
        }
        return List.copyOf(out);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("|", " ").replace(":", " ");
    }
}
