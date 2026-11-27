package dev.kairo.gallery;

import dev.kairo.async.FutureState;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.api.SemanticIRValidator;
import dev.kairo.components.basic.BasicUI;
import dev.kairo.components.form.FormController;
import dev.kairo.components.input.SelectOption;
import dev.kairo.components.list.KairoListDiff;
import dev.kairo.components.list.ListWindow;
import dev.kairo.core.Diagnostic;
import dev.kairo.core.StableKey;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.events.CommonEvents;
import dev.kairo.inspector.TreeInspector;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.NodeIR;
import dev.kairo.layout.LayoutConstraints;
import dev.kairo.lifecycle.LifecycleScope;
import dev.kairo.navigation.Router;
import dev.kairo.resources.ResourceManager;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.state.State;
import dev.kairo.testkit.FakeRenderer;
import dev.kairo.testkit.TestScheduler;
import dev.kairo.xml.KairoXmlParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 12 (I.1) Acceptance Suite: verifies all 12 Kairo Gallery categories end-to-end
 * using ONLY public Kairo v1 APIs and FakeRenderer (zero Android mocks required).
 */
final class GalleryAcceptanceTest {

    @Test
    void exercisesAllTwelvePublicApiCategoriesEndToEnd() throws Exception {
        // 01-06. Public registry, XML + .kcss validation, BasicUI layout & interactive widgets
        ComponentRegistry registry = GalleryApp.createPublicRegistry();
        assertThat(GalleryApp.CATEGORIES).hasSize(12);

        String galleryXml = Files.readString(Path.of("src/main/res/kairo/screens/index.xml"));
        String galleryCss = Files.readString(Path.of("src/main/res/kairo/gallery.css"));

        IRDocument doc = new KairoXmlParser().parseDocument("screens/index.xml", galleryXml);
        Diagnostic.Bag semDiagnostics = new SemanticIRValidator().validate(doc, registry);
        assertThat(semDiagnostics.hasErrors()).isFalse();

        CssStylesheet stylesheet = new KairoCssParser().parse(galleryCss);
        assertThat(stylesheet.diagnostics().hasErrors()).isFalse();

        // Compose full multi-category gallery screen using pure Java BasicUI DSL
        NodeIR screenTree = BasicUI.screen("galleryRoot")
                .classes("gallery")
                .child(BasicUI.column()
                        .id("mainCol")
                        .child(BasicUI.text("Kairo Gallery v1").id("titleText").classes("heading"))
                        .child(BasicUI.row()
                                .id("actionsRow")
                                .child(BasicUI.button("Alternar Tema").id("themeBtn").on("onClick", "toggleTheme"))
                                .child(BasicUI.button("Abrir").id("openBtn").classes("primary").on("onClick", "openCategory"))
                        )
                        .child(BasicUI.stack()
                                .id("previewStack")
                                .child(BasicUI.card()
                                        .id("cardBox")
                                        .child(BasicUI.image("gallery/logo.svg", "Logo Kairo").id("logoImg"))
                                        .child(BasicUI.divider())
                                        .child(BasicUI.progress().prop("indeterminate", "false").prop("value", "0.75"))
                                )
                        )
                )
                .build();

        FakeRenderer renderer = new FakeRenderer();
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        GalleryApp.GalleryScreenModel model = new GalleryApp.GalleryScreenModel();

        runtime.mount(screenTree, stylesheet, model, LayoutConstraints.fixed(390, 844));
        assertThat(renderer.findByLogicalId("galleryRoot")).isNotNull();
        assertThat(renderer.findByLogicalId("openBtn").bounds.width()).isGreaterThan(0);

        // 04. Selection options serialization
        List<SelectOption> options = SelectOption.parse(GalleryApp.serializedSelectCatalog());
        assertThat(options).hasSize(3);
        assertThat(options.get(2).disabled()).isTrue();

        // 05. Local resources & path-traversal rejection
        ResourceManager resources = GalleryApp.createLocalResources();
        assertThat(resources.resolveText("gallery/logo.svg").orElseThrow()).contains("<svg");
        assertThatThrownBy(() -> resources.resolve("../secrets.txt")).isInstanceOf(SecurityException.class);

        // 07. Forms with ValidateOn + password redaction
        FormController form = GalleryApp.createSignupForm();
        form.setValue("email", "invalido");
        form.blur("email");
        assertThat(form.field("email").hasError.get()).isTrue();

        form.setValue("email", "equipa@kairo.dev");
        form.setValue("password", "Secret99!");
        form.setValue("confirmPassword", "Secret99!");
        assertThat(form.submit(() -> {})).isTrue();
        Map<String, String> safe = form.safeSnapshot();
        assertThat(safe.get("email")).isEqualTo("equipa@kairo.dev");
        assertThat(safe.get("password")).isEqualTo("••••••");

        // 08 & 12. Stress + List virtualization over 10,000 items & StableKey diffing
        List<String> tenThousand = GalleryApp.buildStressItems(10_000);
        ListWindow.WindowRange window = GalleryApp.computeVirtualizedWindow(tenThousand.size(), 2800.0, 600.0);
        assertThat(window.slice(tenThousand).size()).isLessThan(25);
        assertThat(window.totalContentHeight()).isEqualTo(560_000.0);

        List<KairoListDiff.Op<StableKey>> diff = new KairoListDiff().diff(
                List.of(StableKey.of("a"), StableKey.of("b")),
                List.of(StableKey.of("b"), StableKey.of("a"), StableKey.of("c")),
                k -> k
        );
        assertThat(diff).anyMatch(op -> op instanceof KairoListDiff.Op.Move<?>)
                .anyMatch(op -> op instanceof KairoListDiff.Op.Insert<?>);

        // 09. Navigation Router with query params & guards
        State<Boolean> authed = State.of(false);
        Router router = GalleryApp.createRouter(authed);
        assertThat(router.navigate("/category/stress")).isTrue();
        assertThat(router.current().get().screenName()).isEqualTo("LoginScreen");
        authed.set(true);
        assertThat(router.navigate("/category/stress?mode=fast")).isTrue();
        assertThat(router.current().get().queryParams()).containsEntry("mode", "fast");
        assertThat(router.canGoBack.get()).isTrue();

        // 10. Cooperative Async with TestScheduler & LifecycleScope unmount cancellation
        TestScheduler worker = new TestScheduler();
        TestScheduler ui = new TestScheduler();
        LifecycleScope scope = new LifecycleScope();
        scope.onMount();
        FutureState<String> okLoad = GalleryApp.loadRemotePreview(scope, worker, ui, false);
        worker.flushAll();
        ui.flushAll();
        assertThat(okLoad.status.get()).isEqualTo(FutureState.Status.SUCCESS);
        assertThat(okLoad.value.get()).isEqualTo("Preview v1 ready");

        FutureState<String> cancelledLoad = GalleryApp.loadRemotePreview(scope, worker, ui, false);
        scope.onUnmount();
        assertThat(cancelledLoad.status.get()).isEqualTo(FutureState.Status.CANCELLED);

        // 11. Theme switching + Cascade StyleTrace + Inspector snapshot
        runtime.setTheme("light");
        assertThat(renderer.findByLogicalId("galleryRoot").style.get("background")).isEqualTo("#FAFAFA");
        renderer.dispatch(new CommonEvents.ClickEvent(renderer.findByLogicalId("openBtn").id()));
        assertThat(model.clickCount.get()).isEqualTo(1);

        TreeInspector inspector = new TreeInspector();
        assertThat(inspector.dumpStyleTraceByLogicalId(runtime, "galleryRoot")).contains("theme=light");
        assertThat(inspector.dumpSnapshot(runtime, model, renderer)).contains("backend=fake-testkit");

        runtime.destroy();
    }
}
