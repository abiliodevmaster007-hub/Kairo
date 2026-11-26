package dev.kairo.style;

import dev.kairo.core.Diagnostic;

import java.util.List;
import java.util.Map;

/**
 * Full style resolution output: computed properties, trace entries in cascade order, effective tokens, and diagnostics.
 */
public record StyleResolutionResult(
        ComputedStyle style,
        List<StyleTrace.Entry> trace,
        Map<String, String> effectiveTokens,
        Diagnostic.Bag diagnostics
) {}
