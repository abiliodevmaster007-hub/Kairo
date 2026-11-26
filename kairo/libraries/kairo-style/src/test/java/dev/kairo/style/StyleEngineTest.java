package dev.kairo.style;

import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class StyleEngineTest {
    private final KairoCssParser cssParser = new KairoCssParser();
    private final StyleEngine styleEngine = new StyleEngine();

    @Test
    void idSelectorOutranksTwelveClassesWithoutOverflow() {
        String source = """
                .c1.c2.c3.c4.c5.c6.c7.c8.c9.c10.c11.c12 {
                    background: #DC2626;
                }
                #submit {
                    background: #4F46E5;
                }
                Button {
                    background: #334155;
                    padding: 12;
                }
                """;
        CssStylesheet sheet = cssParser.parse(source);
        var ctx = new StyleEngine.StyleContext(
                "Button",
                "submit",
                List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7", "c8", "c9", "c10", "c11", "c12"),
                EnumSet.noneOf(VisualState.class),
                List.of()
        );

        ComputedStyle computed = styleEngine.resolve(sheet, ctx, ComputedStyle.empty(), Map.of());
        assertThat(computed.get("background")).isEqualTo("#4F46E5");
        assertThat(computed.getPx("padding", 0)).isEqualTo(12.0);
    }

    @Test
    void tiesBreakBySourceOrderAndMatchChainedVisualStates() {
        String source = """
                Button.primary { background: #4F46E5; }
                Button.primary { background: #4338CA; }
                Button.primary:focused:pressed { background: #312E81; }
                Button:disabled { opacity: 0.45; }
                """;
        CssStylesheet sheet = cssParser.parse(source);
        var focusedPressed = new StyleEngine.StyleContext(
                "Button", "btn", List.of("primary"),
                EnumSet.of(VisualState.FOCUSED, VisualState.PRESSED), List.of()
        );
        ComputedStyle pressedStyle = styleEngine.resolve(sheet, focusedPressed, ComputedStyle.empty(), Map.of());
        assertThat(pressedStyle.get("background")).isEqualTo("#312E81");

        var onlyPrimary = new StyleEngine.StyleContext(
                "Button", "btn", List.of("primary"),
                EnumSet.noneOf(VisualState.class), List.of()
        );
        ComputedStyle baseStyle = styleEngine.resolve(sheet, onlyPrimary, ComputedStyle.empty(), Map.of());
        assertThat(baseStyle.get("background")).isEqualTo("#4338CA");
    }

    @Test
    void hoverSelectorIsCapabilityGatedOnTouchAndRecordedInTrace() {
        String source = """
                Button { background: #1E293B; }
                Button:hover { background: #4F46E5; }
                """;
        CssStylesheet sheet = cssParser.parse(source);
        var hoveredCtx = new StyleEngine.StyleContext(
                "Button", "cta", List.of(),
                EnumSet.of(VisualState.HOVER), List.of()
        );

        StyleResolutionResult touchResult = styleEngine.resolveWithTrace(
                sheet, hoveredCtx, ComputedStyle.empty(),
                new ThemeRegistry(), "dark", Map.of(), null, ViewportCapabilities.android(2.0)
        );
        assertThat(touchResult.style().get("background")).isEqualTo("#1E293B");
        assertThat(touchResult.trace())
                .anyMatch(e -> e.origin() == StyleTrace.Origin.CAPABILITY_SKIPPED && "Button:hover".equals(e.selectorOrLabel()));

        StyleResolutionResult pointerResult = styleEngine.resolveWithTrace(
                sheet, hoveredCtx, ComputedStyle.empty(),
                new ThemeRegistry(), "dark", Map.of(), null, ViewportCapabilities.desktopPointer(1.0)
        );
        assertThat(pointerResult.style().get("background")).isEqualTo("#4F46E5");
    }

    @Test
    void layeredThemesSupportFallbackTokensAndDetectCycles() {
        String source = """
                Theme {
                    --accent: #4F46E5;
                    --accent-alias: token(--accent);
                    --cycle-a: token(--cycle-b);
                    --cycle-b: token(--cycle-a);
                }
                Theme light {
                    --surface: #FFFFFF;
                }
                Theme dark {
                    --surface: #09090B;
                }
                Screen {
                    background: token(--surface);
                    color: token(--missing-text, #E4E4E7);
                    border-color: token(--cycle-a, #888888);
                }
                Button {
                    background: token(--accent-alias);
                }
                """;
        CssStylesheet sheet = cssParser.parse(source);
        ThemeRegistry registry = new ThemeRegistry();
        var screenCtx = new StyleEngine.StyleContext("Screen", "root", List.of(), EnumSet.noneOf(VisualState.class), List.of());

        registry.setActiveTheme("dark");
        StyleResolutionResult dark = styleEngine.resolveWithTrace(
                sheet, screenCtx, ComputedStyle.empty(), registry, null, Map.of(), null, ViewportCapabilities.DEFAULT
        );
        assertThat(dark.style().get("background")).isEqualTo("#09090B");
        assertThat(dark.style().get("color")).isEqualTo("#E4E4E7");
        assertThat(dark.style().get("border-color")).isEqualTo("#888888");
        assertThat(dark.diagnostics().hasErrors()).isTrue();
        assertThat(dark.diagnostics().items()).anyMatch(d -> "STYLE_TOKEN_CYCLE".equals(d.code()));

        registry.setActiveTheme("light");
        StyleResolutionResult light = styleEngine.resolveWithTrace(
                sheet, screenCtx, ComputedStyle.empty(), registry, null, Map.of(), null, ViewportCapabilities.DEFAULT
        );
        assertThat(light.style().get("background")).isEqualTo("#FFFFFF");
    }

    @Test
    void inheritsOnlyTypographyAndResolvesDescendantInlineAndEdgeInsets() {
        String source = """
                .login-page Text#title { font-size: 22; }
                """;
        CssStylesheet sheet = cssParser.parse(source);

        ComputedStyle parentStyle = new ComputedStyle(Map.of(
                "color", "#FAFAFA",
                "font-family", "Inter",
                "background", "#111115",
                "padding", "24"
        ));

        var ancestor = new StyleEngine.StyleContext("Screen", "login", List.of("login-page"), EnumSet.noneOf(VisualState.class), List.of());
        var childCtx = new StyleEngine.StyleContext("Text", "title", List.of(), EnumSet.noneOf(VisualState.class), List.of(ancestor));

        StyleResolutionResult res = styleEngine.resolveWithTrace(
                sheet, childCtx, parentStyle, new ThemeRegistry(), "dark",
                Map.of(), "color: #4F46E5; margin: 8 16 12 20", ViewportCapabilities.DEFAULT
        );

        assertThat(res.style().get("font-family")).isEqualTo("Inter");
        assertThat(res.style().get("background")).isNull();
        assertThat(res.style().get("padding")).isNull();
        assertThat(res.style().getPx("font-size", 0)).isEqualTo(22.0);
        // Inline overrides inherited color
        assertThat(res.style().get("color")).isEqualTo("#4F46E5");

        ComputedStyle.EdgeInsets margin = res.style().getInsets("margin");
        assertThat(margin).isEqualTo(new ComputedStyle.EdgeInsets(8, 16, 12, 20));
        assertThat(margin.horizontal()).isEqualTo(36.0);
    }
}
