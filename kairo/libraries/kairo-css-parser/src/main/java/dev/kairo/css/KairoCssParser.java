package dev.kairo.css;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;
import dev.kairo.ir.KairoStyleDialect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Kairo's native UI style dialect (.kcss) — NOT full web CSS.
 * Supports @import, Theme / Theme <name> blocks, type/class/id/descendant selectors,
 * and visual state pseudo-classes while rejecting web-only selectors and properties.
 */
public final class KairoCssParser {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("@import\\s+[\"']([^\"']+)[\"']\\s*;");
    private static final Pattern BLOCK_PATTERN = Pattern.compile("([^{}]+)\\{([^{}]*)\\}", Pattern.DOTALL);

    public record ParseResult(CssStylesheet stylesheet, Diagnostic.Bag diagnostics) {}

    public CssStylesheet parse(String cssSource) {
        return parseWithDiagnostics("<inline.kcss>", cssSource).stylesheet();
    }

    public ParseResult parseWithDiagnostics(String fileName, String cssSource) {
        Diagnostic.Bag diagnostics = new Diagnostic.Bag();
        String cleaned = cssSource.replaceAll("/\\*.*?\\*/", "");

        List<String> imports = new ArrayList<>();
        Matcher importMatcher = IMPORT_PATTERN.matcher(cleaned);
        StringBuilder withoutImports = new StringBuilder();
        while (importMatcher.find()) {
            imports.add(importMatcher.group(1).trim());
            importMatcher.appendReplacement(withoutImports, "");
        }
        importMatcher.appendTail(withoutImports);
        String bodyText = withoutImports.toString();

        if (bodyText.contains("@media") || bodyText.contains("@keyframes")) {
            diagnostics.error(
                    "STYLE_WEB_ATRULE_FORBIDDEN",
                    "Web @media/@keyframes at-rules are not supported in Kairo's native style dialect.",
                    new SourceLocation(fileName, 1, 1)
            );
        }

        Map<String, String> baseTokens = new LinkedHashMap<>();
        Map<String, Map<String, String>> namedThemes = new LinkedHashMap<>();
        List<CssStylesheet.Rule> rules = new ArrayList<>();

        Matcher matcher = BLOCK_PATTERN.matcher(bodyText);
        int order = 0;
        while (matcher.find()) {
            int line = countLinesUpTo(bodyText, matcher.start());
            SourceLocation blockLoc = new SourceLocation(fileName, line, 1);

            String selectorRaw = matcher.group(1).trim();
            String blockBody = matcher.group(2).trim();
            Map<String, String> decls = parseDeclarations(blockBody);

            if ("Theme".equals(selectorRaw) || ":root".equals(selectorRaw)) {
                baseTokens.putAll(decls);
            } else if (selectorRaw.startsWith("Theme ")) {
                String themeName = selectorRaw.substring("Theme ".length()).trim().toLowerCase();
                if (themeName.isEmpty()) {
                    diagnostics.error("STYLE_THEME_NAME_EMPTY", "Theme block requires a non-empty name", blockLoc);
                } else {
                    namedThemes.computeIfAbsent(themeName, ignored -> new LinkedHashMap<>()).putAll(decls);
                }
            } else {
                for (var entry : decls.entrySet()) {
                    KairoStyleDialect.validateDeclaration(entry.getKey(), entry.getValue(), blockLoc, diagnostics);
                }
                for (String singleSelector : selectorRaw.split(",")) {
                    String s = singleSelector.trim();
                    if (!s.isEmpty()) {
                        validateKairoSelector(s, blockLoc, diagnostics);
                        rules.add(new CssStylesheet.Rule(s, decls, order++, blockLoc));
                    }
                }
            }
        }
        return new ParseResult(new CssStylesheet(imports, baseTokens, namedThemes, rules), diagnostics);
    }

    private void validateKairoSelector(String selector, SourceLocation location, Diagnostic.Bag diagnostics) {
        if (selector.contains(">") || selector.contains("+") || selector.contains("~")
                || selector.contains("[") || selector.contains("::") || selector.contains("*")) {
            diagnostics.error(
                    "STYLE_WEB_SELECTOR_UNSUPPORTED",
                    "Web CSS selector syntax ('" + selector + "') is unsupported; use type, .class, #id, descendant, and :state.",
                    location
            );
        }
    }

    private Map<String, String> parseDeclarations(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String statement : body.split(";")) {
            String trimmed = statement.trim();
            if (trimmed.isEmpty()) continue;
            int colon = trimmed.indexOf(':');
            if (colon > 0) {
                String prop = trimmed.substring(0, colon).trim();
                String val = trimmed.substring(colon + 1).trim();
                map.put(prop, val);
            }
        }
        return map;
    }

    private int countLinesUpTo(String text, int index) {
        int lines = 1;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') lines++;
        }
        return lines;
    }
}
