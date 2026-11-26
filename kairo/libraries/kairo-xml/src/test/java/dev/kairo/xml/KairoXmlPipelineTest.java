package dev.kairo.xml;

import dev.kairo.ir.IRDocument;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

final class KairoXmlPipelineTest {
    @Test
    void parsesDocumentWithImportsStyleAndSourceLocations() {
        String xml = """
                <kairo>
                  <import component="dev.kairo.gallery.UserCard" as="UserCard"/>
                  <style src="styles/login.kcss"/>
                  <Screen id="login" class="login-page">
                    <Column gap="16" padding="24">
                      <Text id="title" value="Entrar"/>
                      <Input id="email" bind:value="email" placeholder="Email"/>
                      <Button id="submit" disabled="{{ !canSubmit }}" onClick="submit">Entrar</Button>
                    </Column>
                  </Screen>
                </kairo>
                """;

        IRDocument doc = new KairoXmlParser().parseDocument("login.xml", xml);
        assertThat(doc.diagnostics().hasErrors()).isFalse();
        assertThat(doc.styleImports()).containsExactly("styles/login.kcss");
        assertThat(doc.componentImports()).containsEntry("UserCard", "dev.kairo.gallery.UserCard");
        assertThat(doc.root().componentType()).isEqualTo("Screen");
        assertThat(doc.root().sourceLocation().file()).isEqualTo("login.xml");
        assertThat(doc.root().sourceLocation().line()).isEqualTo(4);
    }

    @Test
    void rejectsDtdAndExternalEntitiesForXxeSafety() {
        String malicious = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <Screen id="bad"><Text value="&xxe;"/></Screen>
                """;

        IRDocument doc = new KairoXmlParser().parseDocument("evil.xml", malicious);
        assertThat(doc.diagnostics().hasErrors()).isTrue();
        assertThat(doc.diagnostics().all()).anyMatch(d -> d.code().equals("XML_SYNTAX"));
    }

    @Test
    void rejectsUnsafeExpressionsAndWebOnlyInlineCss() {
        String xml = """
                <Screen id="s1">
                  <Text id="t1" style="float: left; position: fixed;" value="{{ Runtime.getRuntime().exec() }}"/>
                </Screen>
                """;

        IRDocument doc = new KairoXmlParser().parseDocument("bad_expr.xml", xml);
        assertThat(doc.diagnostics().hasErrors()).isTrue();
        assertThat(doc.diagnostics().all())
                .extracting(d -> d.code())
                .contains("STYLE_WEB_CSS_UNSUPPORTED", "EXPR_UNSAFE");
    }
}
