package dev.kairo.renderer.android;

import dev.kairo.layout.LayoutBounds;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;

import java.util.Map;
import java.util.Set;

/**
 * Encodes Kairo ComputedStyle, LayoutBounds, props and VisualStates into a compact
 * primitive snapshot shared cleanly between Java Canvas/ViewGroup rendering and the C++ scene graph.
 */
public final class KairoSceneEncoder {
    public static final int FLAG_DISABLED = 1;
    public static final int FLAG_FOCUSED = 1 << 1;
    public static final int FLAG_PRESSED = 1 << 2;
    public static final int FLAG_ERROR = 1 << 3;
    public static final int FLAG_CHECKED = 1 << 4;
    public static final int FLAG_SELECTED = 1 << 5;

    public record EncodedVisualNode(
            LayoutBounds bounds,
            int backgroundArgb,
            int borderArgb,
            float borderWidthPx,
            float cornerRadiusPx,
            float opacity,
            int visualFlags,
            boolean clipChildren,
            boolean visible,
            int textArgb,
            float fontSizePx,
            boolean bold,
            String textAlign
    ) {}

    private KairoSceneEncoder() {}

    public static int encodeVisualFlags(Set<VisualState> states) {
        if (states == null || states.isEmpty()) {
            return 0;
        }
        int flags = 0;
        for (VisualState state : states) {
            switch (state) {
                case DISABLED -> flags |= FLAG_DISABLED;
                case FOCUSED -> flags |= FLAG_FOCUSED;
                case PRESSED -> flags |= FLAG_PRESSED;
                case ERROR -> flags |= FLAG_ERROR;
                case CHECKED -> flags |= FLAG_CHECKED;
                case SELECTED -> flags |= FLAG_SELECTED;
                case HOVER -> {
                    // Android touch surfaces ignore hover unless explicitly enabled.
                }
            }
        }
        return flags;
    }

    public static EncodedVisualNode encode(
            String componentType,
            ComputedStyle style,
            LayoutBounds bounds,
            Set<VisualState> states,
            Map<String, String> props) {
        ComputedStyle safeStyle = style != null ? style : ComputedStyle.empty();
        LayoutBounds safeBounds = bounds != null ? bounds : LayoutBounds.ZERO;
        int flags = encodeVisualFlags(states);

        String bgStr = safeStyle.get("background");
        if (bgStr == null) {
            bgStr = safeStyle.get("background-color");
        }
        int defaultBg = "Divider".equals(componentType) ? 0xFF27272A : 0x00000000;
        int bgArgb = KairoColorParser.parseArgb(bgStr, defaultBg);

        String borderColorStr = safeStyle.get("border-color");
        float borderWidth = (float) Math.max(0.0, safeStyle.getPx("border-width", 0.0));
        int borderArgb = KairoColorParser.parseArgb(borderColorStr, 0x00000000);
        if ((flags & FLAG_ERROR) != 0 && !KairoColorParser.hasVisibleAlpha(borderArgb)) {
            borderArgb = 0xFFEF4444;
            if (borderWidth <= 0.0f) {
                borderWidth = 1.5f;
            }
        } else if ((flags & FLAG_FOCUSED) != 0 && !KairoColorParser.hasVisibleAlpha(borderArgb)
                && ("Input".equals(componentType) || "TextArea".equals(componentType))) {
            borderArgb = 0xFF4F46E5;
            if (borderWidth <= 0.0f) {
                borderWidth = 1.5f;
            }
        }

        float radius = (float) Math.max(0.0, safeStyle.getPx("radius",
                safeStyle.getPx("border-radius", "Card".equals(componentType) ? 12.0 : 0.0)));

        float opacity = (float) Math.max(0.0, Math.min(1.0, safeStyle.getDouble("opacity", 1.0)));
        if ((flags & FLAG_DISABLED) != 0 && opacity > 0.55f) {
            opacity = 0.55f;
        }

        String overflow = safeStyle.get("overflow");
        if (overflow == null && props != null) {
            overflow = props.get("overflow");
        }
        boolean clipChildren = "clip".equalsIgnoreCase(overflow)
                || "hidden".equalsIgnoreCase(overflow)
                || "scroll".equalsIgnoreCase(overflow)
                || "Scroll".equals(componentType)
                || "List".equals(componentType);

        boolean visible = true;
        if (props != null && "false".equalsIgnoreCase(props.get("visible"))) {
            visible = false;
        }

        int textArgb = KairoColorParser.parseArgb(safeStyle.get("color"), 0xFFF4F4F5);
        float fontSize = (float) safeStyle.getPx("font-size", -1.0);
        String fontWeight = safeStyle.get("font-weight");
        boolean bold = "bold".equalsIgnoreCase(fontWeight) || "600".equals(fontWeight) || "700".equals(fontWeight);
        String textAlign = safeStyle.get("text-align");

        return new EncodedVisualNode(
                safeBounds,
                bgArgb,
                borderArgb,
                borderWidth,
                radius,
                opacity,
                flags,
                clipChildren,
                visible,
                textArgb,
                fontSize,
                bold,
                textAlign
        );
    }
}
