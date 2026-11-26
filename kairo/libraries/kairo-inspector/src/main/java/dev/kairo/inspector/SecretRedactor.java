package dev.kairo.inspector;

import dev.kairo.runtime.VirtualNode;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Redacts sensitive property/binding values (passwords, tokens, secrets, PINs, CVVs, API keys)
 * so inspector tree dumps, binding dumps, and CLI diagnostics never leak secrets.
 */
public final class SecretRedactor {
    public static final String REDACTED_MASK = "••••••";

    private static final List<String> SENSITIVE_SUBSTRINGS = List.of(
            "password", "passwd", "pwd", "secret", "token", "apikey", "api_key",
            "pin", "cvv", "credential", "private_key", "privatekey", "bearer"
    );

    private static final Set<String> MASKABLE_VALUE_PROPS = Set.of("value", "text", "defaultvalue");

    private SecretRedactor() {}

    public static boolean isSensitiveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return false;
        }
        String lower = identifier.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE_SUBSTRINGS) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSensitiveNode(VirtualNode node) {
        if (node == null) return false;
        if ("password".equalsIgnoreCase(node.props().get("type"))) return true;
        if ("true".equalsIgnoreCase(node.props().get("sensitive"))) return true;
        if ("PasswordInput".equals(node.componentType())) return true;
        if (isSensitiveIdentifier(node.logicalId())) return true;
        return isSensitiveIdentifier(node.props().get("__bind:value"));
    }

    public static String redactNodeProp(VirtualNode node, String propKey, String propValue) {
        if (propValue == null) return null;
        String lowerKey = propKey == null ? "" : propKey.toLowerCase(Locale.ROOT);
        if (isSensitiveIdentifier(lowerKey)) {
            return REDACTED_MASK;
        }
        if (isSensitiveNode(node) && MASKABLE_VALUE_PROPS.contains(lowerKey)) {
            return REDACTED_MASK;
        }
        return propValue;
    }

    public static String redactBindingValue(String bindingName, Object value) {
        if (isSensitiveIdentifier(bindingName)) {
            return REDACTED_MASK;
        }
        return String.valueOf(value);
    }
}
