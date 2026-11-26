package dev.kairo.runtime;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.input.InputComponents;
import dev.kairo.components.list.KairoListDiff;
import dev.kairo.core.StableKey;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.examples.login.LoginScreenModel;
import dev.kairo.inspector.TreeInspector;
import dev.kairo.navigation.Router;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.testkit.EventTestDriver;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.Fixtures;
import dev.kairo.testkit.TreeAssertions;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class LoginVerticalSliceTest {
    @Test
    void loginVerticalSliceProducesIncrementalPatchesAndMasksPasswords() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);
        InputComponents.registerAll(registry);

        String xml = """
                <Screen id="login" class="login-page">
                  <Column gap="16" padding="24">
                    <Text id="heading" value="Entrar na conta"/>
                    <Input id="email" bind:value="email" placeholder="Email"/>
                    <Input id="password" bind:value="password" type="password" placeholder="Palavra-passe"/>
                    <Text id="feedback" value="{{ feedback }}"/>
                    <Button id="submit" class="primary" disabled="{{ !canSubmit }}" onClick="submit">Entrar</Button>
                  </Column>
                </Screen>
                """;

        var parsed = new KairoXmlParser().parse("login.xml", xml);
        assertThat(parsed.diagnostics().hasErrors()).isFalse();

        CssStylesheet sheet = new KairoCssParser().parse(Fixtures.LOGIN_CSS);
        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        LoginScreenModel model = new LoginScreenModel();
        EventTestDriver driver = new EventTestDriver(renderer);

        runtime.mount(parsed.root(), sheet, model, Fixtures.STANDARD_PHONE);
        assertThat(renderer.findByLogicalId("submit").props.get("disabled")).isEqualTo("true");

        int batchCountAfterMount = runtime.patchHistory().size();

        // Typing invalid email + valid password emits incremental patches without CreateNode
        driver.typeText("email", "invalido");
        driver.typeText("password", "segredo123");

        assertThat(renderer.findByLogicalId("submit").props.get("disabled")).isEqualTo("false");
        TreeAssertions.assertNoNodeCreationsInLastBatch(runtime);

        // Coalesced recomputation: each InputEvent emits exactly 1 patch batch
        assertThat(runtime.patchHistory().size()).isEqualTo(batchCountAfterMount + 2);

        // Submitting invalid email updates feedback text incrementally
        driver.click("submit");
        assertThat(model.isAuthenticated()).isFalse();
        assertThat(renderer.findByLogicalId("feedback").props.get("value"))
                .isEqualTo("Introduza um email válido.");

        // Fixing email and submitting succeeds
        driver.typeText("email", "dev@kairo.dev");
        driver.click("submit");
        assertThat(model.isAuthenticated()).isTrue();
        assertThat(renderer.findByLogicalId("feedback").props.get("value"))
                .isEqualTo("Sessão pronta para dev@kairo.dev");

        // Inspector must mask password value
        String dump = new TreeInspector().dumpTree(runtime);
        assertThat(dump).doesNotContain("segredo123");
        assertThat(dump).contains("••••••");

        runtime.destroy();
    }

    @Test
    void listDiffHandles10000ItemsIncrementallyWithStableKeys() {
        record RowItem(int id, String label) {}
        List<RowItem> prev = new ArrayList<>(10_000);
        List<RowItem> next = new ArrayList<>(10_000);
        for (int i = 0; i < 10_000; i++) {
            prev.add(new RowItem(i, "Item " + i));
            next.add(i == 42 ? new RowItem(i, "Atualizado 42") : new RowItem(i, "Item " + i));
        }

        List<KairoListDiff.Op<RowItem>> ops = new KairoListDiff().diff(prev, next, item -> StableKey.of(item.id()));
        assertThat(ops).hasSize(1);
        assertThat(ops.get(0)).isInstanceOf(KairoListDiff.Op.Update.class);
    }

    @Test
    void routerSupportsParameterizedRoutesAndBackStack() {
        Router router = new Router();
        router.registerRoute("/gallery", "GalleryIndexScreen");
        router.registerRoute("/gallery/:category", "CategoryScreen");

        assertThat(router.navigate("/gallery")).isTrue();
        assertThat(router.navigate("/gallery/forms")).isTrue();
        assertThat(router.current().get().params()).containsEntry("category", "forms");
        assertThat(router.back()).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("GalleryIndexScreen");
    }
}
