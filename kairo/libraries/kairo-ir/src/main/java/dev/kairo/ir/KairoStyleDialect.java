package dev.kairo.ir;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;

import java.util.Set;

/**
 * Defines Kairo's native UI style dialect (NOT browser/web CSS).
 * Rejects web-only layout/rendering features like float, position:fixed, z-index, calc(), or !important.
 */
public final class KairoStyleDialect {
    public static final Set<String> SUPPORTED_PROPERTIES = Set.of(
            // Layout & Sizing (neutral Kairo layout engine)
            "width", "height", "min-width", "max-width", "min-height", "max-height",
            "padding", "margin", "gap", "align", "grow", "shrink", "wrap", "overflow", "axis",
            // Surface & Visuals
            "background", "background-color", "radius", "border-radius",
            "border-color", "border-width", "opacity", "elevation",
            // Typography (inherited)
            "color", "font-size", "font-weight", "font-family", "text-align", "line-height"
    );

    public static final Set<String> FORBIDDEN_WEB_PROPERTIES = Set.of(
            "position", "top", "right", "bottom", "left", "z-index",
            "float", "clear", "display", "grid-template-columns", "grid-template-rows",
            "box-sizing", "transform", "transition", "animation", "cursor", "pointer-events"
    );

    private KairoStyleDialect() {}

    public static void validateDeclaration(
            String property,
            String value,
            SourceLocation location,
            Diagnostic.Bag diagnostics) {
        String prop = property.trim().toLowerCase();
        String val = value.trim();

        if (val.toLowerCase().contains("!important")) {
            diagnostics.error(
                    "STYLE_WEB_IMPORTANT_FORBIDDEN",
                    "Kairo style dialect does not support '!important'; use specificity (ID > class > type) or tokens.",
                    location
            );
        }
        if (val.toLowerCase().contains("calc(")) {
            diagnostics.error(
                    "STYLE_WEB_CALC_FORBIDDEN",
                    "Kairo style dialect does not support web 'calc()'; compute derived sizes in Java state or use Fill weights.",
                    location
            );
        }
        if (FORBIDDEN_WEB_PROPERTIES.contains(prop)) {
            diagnostics.error(
                    "STYLE_WEB_CSS_UNSUPPORTED",
                    "Web CSS property '" + prop + "' is not part of Kairo's native UI style dialect.",
                    location
            );
            return;
        }
        if (!SUPPORTED_PROPERTIES.contains(prop)) {
            diagnostics.warning(
                    "STYLE_UNKNOWN_PROPERTY",
                    "Unknown Kairo style property '" + prop + "'",
                    location
            );
        }
    }
}
