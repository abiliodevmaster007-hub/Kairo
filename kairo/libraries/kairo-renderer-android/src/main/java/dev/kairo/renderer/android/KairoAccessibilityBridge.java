package dev.kairo.renderer.android;

import android.view.View;
import android.widget.TextView;

/**
 * Bridges neutral Kairo accessibility attributes (accessibilityLabel, liveRegion, focusable,
 * accessibilityHidden) onto Android View accessibility semantics cleanly (API 16+ compatible).
 */
public final class KairoAccessibilityBridge {
    private KairoAccessibilityBridge() {}

    public static void applyProperty(View view, String property, String value) {
        if (view == null || property == null) return;
        switch (property) {
            case "accessibilityLabel", "alt" -> view.setContentDescription(value);
            case "accessibilityHidden" -> view.setImportantForAccessibility(
                    "true".equalsIgnoreCase(value)
                            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO
                            : View.IMPORTANT_FOR_ACCESSIBILITY_YES
            );
            case "focusable" -> view.setFocusable(!"false".equalsIgnoreCase(value));
            case "liveRegion" -> {
                if ("polite".equalsIgnoreCase(value) || "assertive".equalsIgnoreCase(value)) {
                    view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
                    if (view instanceof TextView tv && tv.getText() != null && tv.getText().length() > 0) {
                        view.announceForAccessibility(tv.getText());
                    }
                }
            }
            default -> {
                // Non-accessibility property
            }
        }
    }
}
