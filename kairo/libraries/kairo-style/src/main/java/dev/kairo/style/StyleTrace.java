package dev.kairo.style;

import dev.kairo.core.SourceLocation;

import java.util.Map;

/**
 * Audit trail for a single style contributor applied (or skipped) on a virtual node.
 */
public final class StyleTrace {
    public enum Origin {
        INHERITED_PARENT,
        STYLESHEET_RULE,
        INLINE_STYLE,
        CAPABILITY_SKIPPED
    }

    public record Entry(
            Origin origin,
            String selectorOrLabel,
            SelectorSpecificity specificity,
            int sourceOrder,
            SourceLocation location,
            Map<String, String> declarations
    ) {}

    private StyleTrace() {}
}
