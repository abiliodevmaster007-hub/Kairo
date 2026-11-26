package dev.kairo.style;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves token(key) and token(key, fallback) expressions with transitive cycle detection and fallback recovery.
 */
public final class TokenResolver {
    private static final Pattern TOKEN_FN = Pattern.compile("token\\(([^)]+)\\)");

    private static final class TokenCycleException extends RuntimeException {
        TokenCycleException() {
            super(null, null, false, false);
        }
    }

    public String resolve(
            String rawValue,
            Map<String, String> tokens,
            SourceLocation location,
            Diagnostic.Bag diagnostics) {
        if (rawValue == null || !rawValue.contains("token(")) {
            return rawValue;
        }
        try {
            return resolveInternal(rawValue, tokens, new LinkedHashSet<>(), location, diagnostics);
        } catch (TokenCycleException ex) {
            return rawValue;
        }
    }

    private String resolveInternal(
            String value,
            Map<String, String> tokens,
            Set<String> visiting,
            SourceLocation location,
            Diagnostic.Bag diagnostics) {
        Matcher matcher = TOKEN_FN.matcher(value);
        if (!matcher.find()) {
            return value;
        }
        matcher.reset();
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String args = matcher.group(1);
            int commaIdx = args.indexOf(',');
            String key = (commaIdx >= 0 ? args.substring(0, commaIdx) : args).trim();
            String fallback = commaIdx >= 0 ? args.substring(commaIdx + 1).trim() : null;

            String replacement;
            if (!visiting.add(key)) {
                if (diagnostics != null) {
                    diagnostics.error(
                            "STYLE_TOKEN_CYCLE",
                            "Cyclic theme token reference detected for '" + key + "'",
                            location != null ? location : SourceLocation.UNKNOWN
                    );
                }
                if (fallback != null) {
                    replacement = fallback;
                } else {
                    throw new TokenCycleException();
                }
            } else {
                try {
                    String mapped = tokens.get(key);
                    if (mapped != null) {
                        try {
                            replacement = resolveInternal(mapped, tokens, visiting, location, diagnostics);
                        } catch (TokenCycleException innerCycle) {
                            if (fallback != null) {
                                replacement = fallback;
                            } else {
                                throw innerCycle;
                            }
                        }
                    } else if (fallback != null) {
                        replacement = resolveInternal(fallback, tokens, visiting, location, diagnostics);
                    } else {
                        if (diagnostics != null) {
                            diagnostics.warning(
                                    "STYLE_TOKEN_MISSING",
                                    "Undefined theme token '" + key + "'",
                                    location != null ? location : SourceLocation.UNKNOWN
                            );
                        }
                        replacement = matcher.group(0);
                    }
                } finally {
                    visiting.remove(key);
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
