package dev.kairo.gallery;

import dev.kairo.async.FutureState;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.form.FormController;
import dev.kairo.components.input.InputComponents;
import dev.kairo.components.input.SelectOption;
import dev.kairo.components.list.KairoListDiff;
import dev.kairo.components.list.ListWindow;
import dev.kairo.lifecycle.LifecycleScope;
import dev.kairo.navigation.Router;
import dev.kairo.resources.ResourceManager;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.state.Derived;
import dev.kairo.state.ObservableList;
import dev.kairo.state.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Kairo Gallery entry point exercising ONLY public Kairo v1 APIs across all 12 acceptance categories.
 */
public final class GalleryApp {
    public record Category(int id, String slug, String title) {}

    public static final List<Category> CATEGORIES = List.of(
            new Category(1, "layout", "01. Layout (Column, Row, Box, Stack, Scroll)"),
            new Category(2, "text-actions", "02. Texto e ações (Text, Button)"),
            new Category(3, "input", "03. Input (Input, TextArea)"),
            new Category(4, "selection", "04. Seleção (Checkbox, Switch, Select)"),
            new Category(5, "media-surface", "05. Media e superfície (Image, Card, Divider)"),
            new Category(6, "feedback", "06. Feedback (Progress)"),
            new Category(7, "form", "07. Form (Form, FormField, validação)"),
            new Category(8, "lists", "08. Listas (10 000 itens, keys estáveis)"),
            new Category(9, "navigation", "09. Navegação (Router, push/pop/replace)"),
            new Category(10, "async", "10. Async (FutureState, cancelamento)"),
            new Category(11, "themes-states", "11. Temas e estados visuais"),
            new Category(12, "stress", "12. Stress (layouts extremos, texto longo)")
    );

    public static final class GalleryScreenModel implements KairoRuntime.ScreenModel {
        public final ObservableList<Category> categories = ObservableList.of(CATEGORIES);
        public final State<String> searchQuery = State.of("");
        public final State<String> activeCategory = State.of("layout");
        public final State<Integer> clickCount = State.of(0);
        public final State<Boolean> darkTheme = State.of(true);
        public final Derived<String> summaryText = Derived.derive(
                () -> "Tema: " + (darkTheme.get() ? "dark" : "light") + " | Categoria: " + activeCategory.get()
        );

        public void openCategory() {
            clickCount.set(clickCount.get() + 1);
            activeCategory.set("navigation");
        }

        public void toggleTheme() {
            darkTheme.set(!darkTheme.get());
        }

        @Override
        public Map<String, Supplier<Object>> exposedBindings() {
            return Map.of(
                    "categories", () -> categories,
                    "searchQuery", () -> searchQuery,
                    "activeCategory", () -> activeCategory,
                    "summaryText", () -> summaryText,
                    "clickCount", () -> clickCount
            );
        }

        @Override
        public Map<String, State<String>> twoWayStringStates() {
            return Map.of("searchQuery", searchQuery);
        }

        @Override
        public Map<String, Runnable> actions() {
            return Map.of(
                    "openCategory", this::openCategory,
                    "toggleTheme", this::toggleTheme
            );
        }
    }

    public static ComponentRegistry createPublicRegistry() {
        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);
        InputComponents.registerAll(registry);
        FormController.registerAll(registry);
        KairoListDiff.registerAll(registry);
        Router.registerAll(registry);
        return registry;
    }

    public static Router createRouter(State<Boolean> authenticated) {
        Router router = new Router();
        router.registerRoute("/", "GalleryIndexScreen");
        router.registerRoute("/login", "LoginScreen");
        for (Category cat : CATEGORIES) {
            router.registerRoute("/category/" + cat.slug(), "Category_" + cat.slug());
        }
        router.registerRoute("/category/:slug", "CategoryDetailScreen");
        router.registerRoute("*", "NotFoundScreen");
        router.addGuard(dest -> {
            if (dest.path().startsWith("/category/stress") && !authenticated.get()) {
                return new Router.GuardDecision.Redirect("/login");
            }
            return new Router.GuardDecision.Allow();
        });
        router.navigate("/");
        return router;
    }

    public static Router createRouter() {
        return createRouter(State.of(true));
    }

    public static FormController createSignupForm() {
        FormController form = new FormController(FormController.ValidateOn.BLUR);
        form.field("email").addValidator(FormController.Validators.compose(
                FormController.Validators.required("Email obrigatório"),
                FormController.Validators.email("Formato de email inválido")
        ));
        form.field("password").sensitive(true).addValidator(FormController.Validators.compose(
                FormController.Validators.required("Palavra-passe obrigatória"),
                FormController.Validators.minLength(6, "Mínimo de 6 caracteres")
        ));
        form.field("confirmPassword").sensitive(true).addValidator(
                FormController.Validators.matches(form.field("password"), "Palavras-passe não coincidem")
        );
        return form;
    }

    public static String serializedSelectCatalog() {
        return SelectOption.serialize(List.of(
                new SelectOption("android", "Android SDK 26+"),
                new SelectOption("jvm", "JVM TestKit"),
                new SelectOption("ios", "iOS (Research)", true)
        ));
    }

    public static List<String> buildStressItems(int count) {
        List<String> items = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            items.add("row-" + i);
        }
        return items;
    }

    public static ListWindow.WindowRange computeVirtualizedWindow(
            int totalItems,
            double scrollOffsetY,
            double viewportHeight) {
        return ListWindow.compute(totalItems, 56.0, scrollOffsetY, viewportHeight, 3);
    }

    public static FutureState<String> loadRemotePreview(
            LifecycleScope scope,
            FutureState.Scheduler worker,
            FutureState.Scheduler ui,
            boolean shouldFail) {
        return FutureState.run(scope, worker, ui, token -> {
            token.throwIfCancelled();
            if (shouldFail) {
                throw new IllegalStateException("Simulated network timeout");
            }
            return "Preview v1 ready";
        });
    }

    public static ResourceManager createLocalResources() {
        return new ResourceManager(
                ResourceManager.inMemoryBuilder()
                        .putText("gallery/logo.svg", "<svg width=\"24\" height=\"24\"/>", "image/svg+xml")
                        .putBytes("gallery/banner.png", new byte[]{1, 2, 3, 4}, "image/png")
                        .build()
        );
    }
}
