package dev.kairo.style;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;
import dev.kairo.css.CssStylesheet;
import dev.kairo.ir.KairoStyleDialect;
import dev.kairo.ir.NodeIR;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Platform-neutral cascade engine for Kairo's native .kcss dialect.
 * Specificity: (IDs, classes + visual states, component types), source-order tie-break.
 * Supports chained visual states, capability-gated :hover, descendant selectors,
 * layered themes, fallback tokens with cycle protection, inline overrides, and inspector traces.
 */
public final class StyleEngine {
    public record StyleContext(
            String componentType,
            String logicalId,
            List<String> styleClasses,
            Set<VisualState> activeStates,
            List<StyleContext> ancestors
    ) {
        public static StyleContext from(NodeIR ir, Set<VisualState> states, List<StyleContext> ancestors) {
            return new StyleContext(ir.componentType(), ir.logicalId(), ir.styleClasses(), states, ancestors);
        }
    }

    private record MatchedRule(
            SelectorSpecificity specificity,
            CssStylesheet.Rule rule
    ) {}

    private enum MatchOutcome {
        MATCHED,
        MISMATCHED,
        SKIPPED_BY_CAPABILITY
    }

    private record SimpleMatchResult(
            MatchOutcome outcome,
            SelectorSpecificity specificity
    ) {
        static SimpleMatchResult mismatched() {
            return new SimpleMatchResult(MatchOutcome.MISMATCHED, SelectorSpecificity.ZERO);
        }

        static SimpleMatchResult skipped() {
            return new SimpleMatchResult(MatchOutcome.SKIPPED_BY_CAPABILITY, SelectorSpecificity.ZERO);
        }
    }

    private final TokenResolver tokenResolver = new TokenResolver();
    private final ThemeRegistry defaultThemeRegistry = new ThemeRegistry();

    public ComputedStyle resolve(
            CssStylesheet stylesheet,
            StyleContext target,
            ComputedStyle parentStyle,
            Map<String, String> themeOverrides) {
        return resolveWithTrace(
                stylesheet,
                target,
                parentStyle,
                defaultThemeRegistry,
                defaultThemeRegistry.activeTheme(),
                themeOverrides,
                null,
                ViewportCapabilities.DEFAULT
        ).style();
    }

    public ComputedStyle resolve(
            CssStylesheet stylesheet,
            StyleContext target,
            ComputedStyle parentStyle,
            ThemeRegistry themeRegistry,
            String activeTheme,
            Map<String, String> runtimeOverrides,
            String inlineStyle,
            ViewportCapabilities capabilities) {
        return resolveWithTrace(
                stylesheet,
                target,
                parentStyle,
                themeRegistry,
                activeTheme,
                runtimeOverrides,
                inlineStyle,
                capabilities
        ).style();
    }

    public StyleResolutionResult resolveWithTrace(
            CssStylesheet stylesheet,
            StyleContext target,
            ComputedStyle parentStyle,
            ThemeRegistry themeRegistry,
            String activeTheme,
            Map<String, String> runtimeOverrides,
            String inlineStyle,
            ViewportCapabilities capabilities) {
        ViewportCapabilities caps = capabilities != null ? capabilities : ViewportCapabilities.DEFAULT;
        ThemeRegistry registry = themeRegistry != null ? themeRegistry : defaultThemeRegistry;
        Diagnostic.Bag diagnostics = new Diagnostic.Bag();
        List<StyleTrace.Entry> trace = new ArrayList<>();
        Map<String, String> resolved = new LinkedHashMap<>();

        if (parentStyle != null && !parentStyle.asMap().isEmpty()) {
            Map<String, String> inheritedProps = new LinkedHashMap<>();
            for (var entry : parentStyle.asMap().entrySet()) {
                if (ComputedStyle.isInherited(entry.getKey())) {
                    resolved.put(entry.getKey(), entry.getValue());
                    inheritedProps.put(entry.getKey(), entry.getValue());
                }
            }
            if (!inheritedProps.isEmpty()) {
                trace.add(new StyleTrace.Entry(
                        StyleTrace.Origin.INHERITED_PARENT,
                        "<parent-typography>",
                        SelectorSpecificity.ZERO,
                        -1,
                        SourceLocation.UNKNOWN,
                        inheritedProps
                ));
            }
        }

        Map<String, String> tokens = registry.resolveEffectiveTokens(stylesheet, activeTheme, runtimeOverrides);
        List<MatchedRule> matches = new ArrayList<>();

        if (stylesheet != null) {
            for (CssStylesheet.Rule rule : stylesheet.rules()) {
                SimpleMatchResult result = matchSelector(rule.selector(), target, caps);
                if (result.outcome() == MatchOutcome.MATCHED) {
                    matches.add(new MatchedRule(result.specificity(), rule));
                } else if (result.outcome() == MatchOutcome.SKIPPED_BY_CAPABILITY) {
                    trace.add(new StyleTrace.Entry(
                            StyleTrace.Origin.CAPABILITY_SKIPPED,
                            rule.selector(),
                            result.specificity(),
                            rule.sourceOrder(),
                            rule.location(),
                            rule.declarations()
                    ));
                }
            }
        }

        matches.sort(Comparator
                .comparing(MatchedRule::specificity)
                .thenComparingInt(m -> m.rule().sourceOrder()));

        for (MatchedRule match : matches) {
            Map<String, String> applied = new LinkedHashMap<>();
            for (var decl : match.rule().declarations().entrySet()) {
                String value = tokenResolver.resolve(decl.getValue(), tokens, match.rule().location(), diagnostics);
                resolved.put(decl.getKey(), value);
                applied.put(decl.getKey(), value);
            }
            trace.add(new StyleTrace.Entry(
                    StyleTrace.Origin.STYLESHEET_RULE,
                    match.rule().selector(),
                    match.specificity(),
                    match.rule().sourceOrder(),
                    match.rule().location(),
                    applied
            ));
        }

        if (inlineStyle != null && !inlineStyle.isBlank()) {
            Map<String, String> inlineApplied = new LinkedHashMap<>();
            for (String statement : inlineStyle.split(";")) {
                String trimmed = statement.trim();
                if (trimmed.isEmpty()) continue;
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    String prop = trimmed.substring(0, colon).trim().toLowerCase();
                    String rawVal = trimmed.substring(colon + 1).trim();
                    KairoStyleDialect.validateDeclaration(prop, rawVal, SourceLocation.UNKNOWN, diagnostics);
                    String val = tokenResolver.resolve(rawVal, tokens, SourceLocation.UNKNOWN, diagnostics);
                    resolved.put(prop, val);
                    inlineApplied.put(prop, val);
                }
            }
            if (!inlineApplied.isEmpty()) {
                trace.add(new StyleTrace.Entry(
                        StyleTrace.Origin.INLINE_STYLE,
                        "<inline style>",
                        new SelectorSpecificity(Integer.MAX_VALUE, 0, 0),
                        Integer.MAX_VALUE,
                        SourceLocation.UNKNOWN,
                        inlineApplied
                ));
            }
        }

        return new StyleResolutionResult(new ComputedStyle(resolved), List.copyOf(trace), tokens, diagnostics);
    }

    private SimpleMatchResult matchSelector(String selector, StyleContext target, ViewportCapabilities capabilities) {
        String[] parts = selector.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return SimpleMatchResult.mismatched();

        SimpleMatchResult leaf = matchSimpleSelector(parts[parts.length - 1], target, capabilities);
        if (leaf.outcome() != MatchOutcome.MATCHED) {
            return leaf;
        }

        SelectorSpecificity totalSpec = leaf.specificity();
        int ancestorCursor = target.ancestors().size() - 1;
        for (int i = parts.length - 2; i >= 0; i--) {
            boolean found = false;
            while (ancestorCursor >= 0) {
                SimpleMatchResult anc = matchSimpleSelector(parts[i], target.ancestors().get(ancestorCursor), capabilities);
                ancestorCursor--;
                if (anc.outcome() == MatchOutcome.SKIPPED_BY_CAPABILITY) {
                    return SimpleMatchResult.skipped();
                }
                if (anc.outcome() == MatchOutcome.MATCHED) {
                    totalSpec = totalSpec.add(anc.specificity());
                    found = true;
                    break;
                }
            }
            if (!found) return SimpleMatchResult.mismatched();
        }
        return new SimpleMatchResult(MatchOutcome.MATCHED, totalSpec);
    }

    private SimpleMatchResult matchSimpleSelector(String simple, StyleContext ctx, ViewportCapabilities capabilities) {
        if (simple.isEmpty()) return SimpleMatchResult.mismatched();

        // Split chained pseudo-classes (e.g. Button.primary:focused:pressed)
        String[] pseudoSplit = simple.split(":");
        String basePart = pseudoSplit[0];

        int statesMatched = 0;
        for (int i = 1; i < pseudoSplit.length; i++) {
            String pseudo = pseudoSplit[i].trim().toUpperCase();
            if (pseudo.isEmpty()) return SimpleMatchResult.mismatched();
            VisualState requiredState;
            try {
                requiredState = VisualState.valueOf(pseudo);
            } catch (IllegalArgumentException ex) {
                return SimpleMatchResult.mismatched();
            }
            if (requiredState == VisualState.HOVER && !capabilities.hoverSupported()) {
                return SimpleMatchResult.skipped();
            }
            if (!ctx.activeStates().contains(requiredState)) {
                return SimpleMatchResult.mismatched();
            }
            statesMatched++;
        }

        String requiredType = null;
        String requiredId = null;
        List<String> requiredClasses = new ArrayList<>();

        int cursor = 0;
        int len = basePart.length();
        while (cursor < len) {
            char marker = basePart.charAt(cursor);
            if (marker == '#' || marker == '.') {
                cursor++;
                int end = cursor;
                while (end < len && basePart.charAt(end) != '#' && basePart.charAt(end) != '.') {
                    end++;
                }
                String name = basePart.substring(cursor, end);
                if (name.isEmpty()) return SimpleMatchResult.mismatched();
                if (marker == '#') {
                    if (requiredId != null && !requiredId.equals(name)) {
                        return SimpleMatchResult.mismatched();
                    }
                    requiredId = name;
                } else {
                    requiredClasses.add(name);
                }
                cursor = end;
            } else {
                int end = cursor;
                while (end < len && basePart.charAt(end) != '#' && basePart.charAt(end) != '.') {
                    end++;
                }
                requiredType = basePart.substring(cursor, end);
                cursor = end;
            }
        }

        int typesCount = 0;
        if (requiredType != null) {
            if (!requiredType.equals(ctx.componentType())) {
                return SimpleMatchResult.mismatched();
            }
            typesCount = 1;
        }

        int idsCount = 0;
        if (requiredId != null) {
            if (!requiredId.equals(ctx.logicalId())) {
                return SimpleMatchResult.mismatched();
            }
            idsCount = 1;
        }

        for (String cls : requiredClasses) {
            if (!ctx.styleClasses().contains(cls)) {
                return SimpleMatchResult.mismatched();
            }
        }

        return new SimpleMatchResult(
                MatchOutcome.MATCHED,
                new SelectorSpecificity(idsCount, requiredClasses.size() + statesMatched, typesCount)
        );
    }
}
