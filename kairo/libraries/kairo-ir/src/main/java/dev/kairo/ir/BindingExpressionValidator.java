package dev.kairo.ir;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;

import java.util.regex.Pattern;

/**
 * Validates binding expressions against Kairo's small, safe expression grammar (no eval, no arbitrary Java).
 * Allowed forms: identifier, !identifier, dotted path (item.name), or simple action call (openUser(item.id)).
 */
public final class BindingExpressionValidator {
    private static final Pattern SAFE_EXPR = Pattern.compile(
            "^!?[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*(\\([a-zA-Z0-9_.,\\s]*\\))?$"
    );

    public void validate(String expression, SourceLocation location, Diagnostic.Bag diagnostics) {
        if (expression == null || expression.isBlank()) {
            diagnostics.error("EXPR_EMPTY", "Binding expression cannot be empty", location);
            return;
        }
        String trimmed = expression.trim();
        if (trimmed.contains(";") || trimmed.contains("=") || trimmed.contains("new ") || trimmed.contains("Runtime")) {
            diagnostics.error(
                    "EXPR_UNSAFE",
                    "Unsafe or imperative construct in binding expression: '" + trimmed + "'",
                    location
            );
            return;
        }
        if (!SAFE_EXPR.matcher(trimmed).matches()) {
            diagnostics.error(
                    "EXPR_SYNTAX",
                    "Invalid Kairo binding expression '" + trimmed + "'. Use identifiers, '!flag', or 'item.field'.",
                    location
            );
        }
    }
}
