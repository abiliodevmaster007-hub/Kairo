package dev.kairo.style;

import dev.kairo.css.CssStylesheet;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manages active theme selection and layered token inheritance:
 * Theme (base) -> Theme &lt;activeTheme&gt; -> programmatic overrides.
 */
public final class ThemeRegistry {
    private String activeTheme = "light";
    private final Map<String, String> programmaticTokens = new LinkedHashMap<>();

    public String activeTheme() {
        return activeTheme;
    }

    public void setActiveTheme(String themeName) {
        if (themeName == null || themeName.isBlank()) {
            throw new IllegalArgumentException("Theme name cannot be blank");
        }
        this.activeTheme = themeName.trim().toLowerCase();
    }

    public void putToken(String key, String value) {
        programmaticTokens.put(key, value);
    }

    public void putTokens(Map<String, String> tokens) {
        if (tokens != null) {
            programmaticTokens.putAll(tokens);
        }
    }

    public Map<String, String> programmaticTokens() {
        return Collections.unmodifiableMap(programmaticTokens);
    }

    public Map<String, String> resolveEffectiveTokens(
            CssStylesheet stylesheet,
            String themeOverrideName,
            Map<String, String> runtimeOverrides) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (stylesheet != null) {
            merged.putAll(stylesheet.tokens());
            String selected = (themeOverrideName != null && !themeOverrideName.isBlank())
                    ? themeOverrideName.trim().toLowerCase()
                    : activeTheme;
            Map<String, String> named = stylesheet.namedThemes().get(selected);
            if (named != null) {
                merged.putAll(named);
            }
        }
        merged.putAll(programmaticTokens);
        if (runtimeOverrides != null) {
            merged.putAll(runtimeOverrides);
        }
        return Collections.unmodifiableMap(merged);
    }
}
