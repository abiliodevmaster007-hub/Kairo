package dev.kairo.renderer.android;

import dev.kairo.layout.LayoutBounds;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class KairoSceneEncoderTest {

    @Test
    void parsesShortLongAndAlphaHexPlusNamedColors() {
        assertThat(KairoColorParser.parseArgb("#4F46E5", 0)).isEqualTo(0xFF4F46E5);
        assertThat(KairoColorParser.parseArgb("#fff", 0)).isEqualTo(0xFFFFFFFF);
        assertThat(KairoColorParser.parseArgb("#80123456", 0)).isEqualTo(0x80123456);
        assertThat(KairoColorParser.parseArgb("transparent", 0xFFFFFFFF)).isEqualTo(0x00000000);
        assertThat(KairoColorParser.parseArgb("invalid", 0xFF112233)).isEqualTo(0xFF112233);
    }

    @Test
    void encodesSurfaceBorderErrorFallbackOpacityAndClipping() {
        ComputedStyle style = new ComputedStyle(Map.of(
                "background", "#18181B",
                "radius", "14px",
                "color", "#FAFAFA",
                "font-size", "18px",
                "font-weight", "bold",
                "text-align", "center",
                "overflow", "clip"
        ));

        KairoSceneEncoder.EncodedVisualNode encoded = KairoSceneEncoder.encode(
                "Input",
                style,
                new LayoutBounds(12, 24, 320, 48),
                Set.of(VisualState.ERROR, VisualState.FOCUSED, VisualState.HOVER),
                Map.of("visible", "true")
        );

        assertThat(encoded.bounds()).isEqualTo(new LayoutBounds(12, 24, 320, 48));
        assertThat(encoded.backgroundArgb()).isEqualTo(0xFF18181B);
        assertThat(encoded.borderArgb()).isEqualTo(0xFFEF4444);
        assertThat(encoded.borderWidthPx()).isEqualTo(1.5f);
        assertThat(encoded.cornerRadiusPx()).isEqualTo(14.0f);
        assertThat(encoded.textArgb()).isEqualTo(0xFFFAFAFA);
        assertThat(encoded.fontSizePx()).isEqualTo(18.0f);
        assertThat(encoded.bold()).isTrue();
        assertThat(encoded.textAlign()).isEqualTo("center");
        assertThat(encoded.clipChildren()).isTrue();
        assertThat(encoded.visible()).isTrue();
        assertThat((encoded.visualFlags() & KairoSceneEncoder.FLAG_ERROR) != 0).isTrue();
        assertThat((encoded.visualFlags() & KairoSceneEncoder.FLAG_FOCUSED) != 0).isTrue();
    }

    @Test
    void disabledStateDimsOpacityAndScrollAlwaysClipsChildren() {
        KairoSceneEncoder.EncodedVisualNode encoded = KairoSceneEncoder.encode(
                "Scroll",
                ComputedStyle.empty(),
                new LayoutBounds(0, 0, 390, 600),
                Set.of(VisualState.DISABLED),
                Map.of("visible", "false")
        );

        assertThat(encoded.opacity()).isEqualTo(0.55f);
        assertThat(encoded.clipChildren()).isTrue();
        assertThat(encoded.visible()).isFalse();
        assertThat((encoded.visualFlags() & KairoSceneEncoder.FLAG_DISABLED) != 0).isTrue();
    }
}
