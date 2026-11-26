package dev.kairo.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Stage 1 (Etapa A) Architecture Gate.
 * Enforces Kairo's invariant rule: neutral modules NEVER import Android, JavaFX, Swing, Skia, GLFW, LWJGL, or Reflections.
 * Only kairo-renderer-android and kairo-platform-android may touch android.* packages.
 */
final class KairoArchitectureTest {
    private static final String[] FORBIDDEN_PLATFORM_PACKAGES = {
            "android..",
            "androidx..",
            "javafx..",
            "javax.swing..",
            "java.awt..",
            "org.jetbrains.skija..",
            "org.lwjgl..",
            "org.reflections.."
    };

    private static final String[] NEUTRAL_PACKAGES = {
            "dev.kairo.annotations..",
            "dev.kairo.core..",
            "dev.kairo.ir..",
            "dev.kairo.xml..",
            "dev.kairo.css..",
            "dev.kairo.style..",
            "dev.kairo.state..",
            "dev.kairo.events..",
            "dev.kairo.lifecycle..",
            "dev.kairo.layout..",
            "dev.kairo.components..",
            "dev.kairo.navigation..",
            "dev.kairo.async..",
            "dev.kairo.resources..",
            "dev.kairo.renderer.api..",
            "dev.kairo.runtime..",
            "dev.kairo.inspector..",
            "dev.kairo.testkit.."
    };

    private static final JavaClasses ALL_CLASSES = new ClassFileImporter().importPackages("dev.kairo");

    @Test
    void neutralModulesMustNeverDependOnPlatformToolkits() {
        noClasses()
                .that().resideInAnyPackage(NEUTRAL_PACKAGES)
                .should().dependOnClassesThat().resideInAnyPackage(FORBIDDEN_PLATFORM_PACKAGES)
                .because("Core, components, layout, style, runtime and testkit must stay 100% platform-independent")
                .check(ALL_CLASSES);
    }

    @Test
    void annotationsMustHaveZeroInternalDependencies() {
        noClasses()
                .that().resideInAPackage("dev.kairo.annotations..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.kairo.core..", "dev.kairo.ir..", "dev.kairo.runtime..", "dev.kairo.components.."
                )
                .because("kairo-annotations is the leaf dependency of the entire monorepo")
                .check(ALL_CLASSES);
    }

    @Test
    void coreMustNotDependOnHigherLayers() {
        noClasses()
                .that().resideInAPackage("dev.kairo.core..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.kairo.ir..",
                        "dev.kairo.xml..",
                        "dev.kairo.css..",
                        "dev.kairo.style..",
                        "dev.kairo.state..",
                        "dev.kairo.runtime..",
                        "dev.kairo.layout..",
                        "dev.kairo.components..",
                        "dev.kairo.renderer.."
                )
                .because("kairo-core only defines contracts, identity, diagnostics and capabilities")
                .check(ALL_CLASSES);
    }

    @Test
    void componentsMustNotDependOnAndroidRendererOrPlatform() {
        noClasses()
                .that().resideInAPackage("dev.kairo.components..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "dev.kairo.renderer.android..",
                        "dev.kairo.platform.android.."
                )
                .because("Every Kairo component must be 100% reusable and decoupled from Android rendering")
                .check(ALL_CLASSES);
    }

    @Test
    void rendererApiMustNeverExposeAndroidTypes() {
        noClasses()
                .that().resideInAPackage("dev.kairo.renderer.api..")
                .should().dependOnClassesThat().resideInAnyPackage("android..", "androidx..")
                .because("RenderHandle and Renderer contracts must protect v2 portability")
                .check(ALL_CLASSES);
    }
}
