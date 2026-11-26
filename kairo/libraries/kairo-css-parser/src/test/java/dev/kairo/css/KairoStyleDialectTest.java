package dev.kairo.css;

import dev.kairo.core.Diagnostic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class KairoStyleDialectTest {
    @Test
    void acceptsValidKairoNativeStyleDialectWithImportsAndNamedThemes() {
        String kcss = """
                @import "tokens/base.kcss";
                Theme {
                  color.primary: #4F46E5;
                  spacing.md: 16px;
                  radius.md: 8px;
                }
                Theme light {
                  color.surface: #FAFAFA;
                }
                Theme dark {
                  color.surface: #09090B;
                }
                Screen.login {
                  background: token(color.surface);
                  padding: 24px;
                }
                Button.primary:pressed {
                  opacity: 0.85;
                  radius: token(radius.md);
                }
                """;

        KairoCssParser.ParseResult result = new KairoCssParser().parseWithDiagnostics("login.kcss", kcss);
        assertThat(result.diagnostics().hasErrors()).isFalse();
        assertThat(result.stylesheet().imports()).containsExactly("tokens/base.kcss");
        assertThat(result.stylesheet().tokens()).containsEntry("color.primary", "#4F46E5");
        assertThat(result.stylesheet().namedThemes().get("light")).containsEntry("color.surface", "#FAFAFA");
        assertThat(result.stylesheet().namedThemes().get("dark")).containsEntry("color.surface", "#09090B");
        assertThat(result.stylesheet().rules()).hasSize(2);
    }

    @Test
    void rejectsWebCssFeaturesAndWebSelectorsUnsupportedByKairoDialect() {
        String webCss = """
                @media (min-width: 768px) {}
                Column > Button::before {
                  position: fixed;
                  float: left;
                  width: calc(100% - 20px);
                  color: #FFF !important;
                }
                """;

        KairoCssParser.ParseResult result = new KairoCssParser().parseWithDiagnostics("web.css", webCss);
        assertThat(result.diagnostics().hasErrors()).isTrue();
        assertThat(result.diagnostics().all())
                .extracting(Diagnostic::code)
                .contains(
                        "STYLE_WEB_ATRULE_FORBIDDEN",
                        "STYLE_WEB_SELECTOR_UNSUPPORTED",
                        "STYLE_WEB_CSS_UNSUPPORTED",
                        "STYLE_WEB_CALC_FORBIDDEN",
                        "STYLE_WEB_IMPORTANT_FORBIDDEN"
                );
    }
}
