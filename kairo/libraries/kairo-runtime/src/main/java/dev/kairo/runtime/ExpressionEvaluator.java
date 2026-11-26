package dev.kairo.runtime;

import dev.kairo.state.Derived;
import dev.kairo.state.State;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Small, safe, reflection-free binding expression evaluator supporting:
 * - identifiers (email, canSubmit)
 * - unary negation (!canSubmit)
 * - dotted property navigation over Map or ExposedObject (user.name, item.title)
 * - automatic unwrapping of State and Derived values
 */
public final class ExpressionEvaluator {
    /**
     * Optional platform-neutral contract for custom DTOs/models exposed in dotted binding paths without reflection.
     */
    @FunctionalInterface
    public interface ExposedObject {
        Object readProperty(String propertyName);
    }

    public Object evaluate(String expression, Map<String, Supplier<Object>> scope) {
        if (expression == null || expression.isBlank()) return "";
        String expr = expression.trim();

        boolean negate = false;
        while (expr.startsWith("!")) {
            negate = !negate;
            expr = expr.substring(1).trim();
        }

        if ("true".equals(expr)) {
            return !negate;
        }
        if ("false".equals(expr)) {
            return negate;
        }

        Object current = resolvePath(expr, scope);
        if (negate) {
            return !asBoolean(current);
        }
        return current == null ? "" : current;
    }

    private Object resolvePath(String expr, Map<String, Supplier<Object>> scope) {
        if (scope == null || expr.isEmpty()) return null;
        String[] segments = expr.split("\\.");
        Supplier<Object> rootSupplier = scope.get(segments[0]);
        Object current = unwrap(rootSupplier != null ? rootSupplier.get() : null);

        for (int i = 1; i < segments.length && current != null; i++) {
            String segment = segments[i];
            if (current instanceof ExposedObject exposed) {
                current = unwrap(exposed.readProperty(segment));
            } else if (current instanceof Map<?, ?> map) {
                current = unwrap(map.get(segment));
            } else {
                return null;
            }
        }
        return current;
    }

    private static Object unwrap(Object value) {
        Object cur = value;
        while (cur instanceof State<?> || cur instanceof Derived<?>) {
            if (cur instanceof State<?> s) {
                cur = s.get();
            } else {
                cur = ((Derived<?>) cur).get();
            }
        }
        return cur;
    }

    public static boolean asBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.doubleValue() != 0.0;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return value != null;
    }
}
