package dev.kairo.css;

import dev.kairo.core.SourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AST for Kairo's native .kcss style dialect (base tokens, named theme overrides, imports, and style rules).
 */
public final class CssStylesheet {
    public record Rule(
            String selector,
            Map<String, String> declarations,
            int sourceOrder,
            SourceLocation location) {
        public Rule {
            declarations = Collections.unmodifiableMap(new LinkedHashMap<>(declarations));
            location = location == null ? SourceLocation.UNKNOWN : location;
        }

        public Rule(String selector, Map<String, String> declarations, int sourceOrder) {
            this(selector, declarations, sourceOrder, SourceLocation.UNKNOWN);
        }
    }

    private final List<String> imports;
    private final Map<String, String> tokens;
    private final Map<String, Map<String, String>> namedThemes;
    private final List<Rule> rules;

    public static CssStylesheet empty() {
        return new CssStylesheet(List.of(), Map.of(), Map.of(), List.of());
    }

    public CssStylesheet(Map<String, String> tokens, List<Rule> rules) {
        this(List.of(), tokens, Map.of(), rules);
    }

    public CssStylesheet(
            List<String> imports,
            Map<String, String> tokens,
            Map<String, Map<String, String>> namedThemes,
            List<Rule> rules) {
        this.imports = List.copyOf(imports);
        this.tokens = Collections.unmodifiableMap(new LinkedHashMap<>(tokens));
        Map<String, Map<String, String>> frozenThemes = new LinkedHashMap<>();
        namedThemes.forEach((name, map) -> frozenThemes.put(name, Collections.unmodifiableMap(new LinkedHashMap<>(map))));
        this.namedThemes = Collections.unmodifiableMap(frozenThemes);
        this.rules = List.copyOf(rules);
    }

    public List<String> imports() { return imports; }
    public Map<String, String> tokens() { return tokens; }
    public Map<String, Map<String, String>> namedThemes() { return namedThemes; }
    public List<Rule> rules() { return rules; }
}
