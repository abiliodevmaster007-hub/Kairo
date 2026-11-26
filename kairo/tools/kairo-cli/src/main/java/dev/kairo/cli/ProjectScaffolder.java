package dev.kairo.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Scaffolds a clean standalone Kairo v1 Java 17 + Android project via official project template.
 */
public final class ProjectScaffolder {
    private static final Pattern VALID_PROJECT_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{0,63}$");
    private static final Pattern VALID_PACKAGE = Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

    public record ScaffoldResult(Path projectRoot, List<Path> createdFiles) {}

    public ScaffoldResult create(String rawName, Path parentDirectory, String basePackage) throws IOException {
        if (rawName == null || !VALID_PROJECT_NAME.matcher(rawName.trim()).matches()) {
            throw new IllegalArgumentException(
                    "Invalid project name '" + rawName + "'. Use letters, digits, '-' or '_' starting with a letter."
            );
        }
        String projectName = rawName.trim();
        String pkg = (basePackage == null || basePackage.isBlank())
                ? "com.example." + projectName.toLowerCase().replace('-', '_')
                : basePackage.trim();
        if (!VALID_PACKAGE.matcher(pkg).matches()) {
            throw new IllegalArgumentException("Invalid Java package name: " + pkg);
        }

        Path root = parentDirectory.toAbsolutePath().normalize().resolve(projectName).normalize();
        if (!root.startsWith(parentDirectory.toAbsolutePath().normalize())) {
            throw new SecurityException("Path traversal rejected when scaffolding project");
        }
        if (Files.exists(root)) {
            throw new IOException("Target project directory already exists: " + root);
        }

        List<Path> created = new ArrayList<>();
        String packagePath = pkg.replace('.', '/');

        created.add(write(root.resolve("settings.gradle.kts"),
                "rootProject.name = \"" + projectName + "\"\n"));

        created.add(write(root.resolve("gradle.properties"), """
                org.gradle.jvmargs=-Xmx1536m
                android.useAndroidX=true
                """));

        created.add(write(root.resolve("build.gradle.kts"), """
                plugins {
                    id("java-library")
                }

                java {
                    toolchain {
                        languageVersion.set(JavaLanguageVersion.of(17))
                    }
                }

                repositories {
                    mavenCentral()
                    google()
                }

                dependencies {
                    implementation("dev.kairo:kairo-core:0.1.0-SNAPSHOT")
                    implementation("dev.kairo:kairo-runtime:0.1.0-SNAPSHOT")
                    implementation("dev.kairo:kairo-components-basic:0.1.0-SNAPSHOT")
                    implementation("dev.kairo:kairo-components-input:0.1.0-SNAPSHOT")
                    implementation("dev.kairo:kairo-renderer-android:0.1.0-SNAPSHOT")
                    implementation("dev.kairo:kairo-platform-android:0.1.0-SNAPSHOT")
                    testImplementation("dev.kairo:kairo-testkit:0.1.0-SNAPSHOT")
                    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
                }

                tasks.withType<Test>().configureEach {
                    useJUnitPlatform()
                }
                """));

        created.add(write(root.resolve("src/main/res/kairo/app.kcss"), """
                Theme {
                  color.primary: #4F46E5;
                  color.surface: #09090B;
                  color.text: #F4F4F5;
                  spacing.md: 16px;
                  radius.md: 8px;
                }

                Theme light {
                  color.surface: #FAFAFA;
                  color.text: #18181B;
                }

                Screen.app-screen {
                  background: token(color.surface);
                  color: token(color.text);
                  padding: token(spacing.md);
                }

                .title {
                  font-size: 22px;
                  font-weight: bold;
                }

                Button.primary {
                  background: token(color.primary);
                  color: #FFFFFF;
                  radius: token(radius.md);
                }
                """));

        created.add(write(root.resolve("src/main/res/kairo/screens/main.xml"), """
                <Screen id="main" class="app-screen">
                  <Column gap="16" padding="24">
                    <Text id="headline" class="title" value="{{ greeting }}"/>
                    <Input id="nameInput" bind:value="name" placeholder="Seu nome"/>
                    <Button id="actionBtn" class="primary" onClick="increment">Incrementar</Button>
                  </Column>
                </Screen>
                """));

        created.add(write(root.resolve("src/main/java/" + packagePath + "/MainScreenModel.java"), """
                package %s;

                import dev.kairo.runtime.KairoRuntime;
                import dev.kairo.state.Derived;
                import dev.kairo.state.State;

                import java.util.Map;
                import java.util.function.Supplier;

                public final class MainScreenModel implements KairoRuntime.ScreenModel {
                    public final State<String> name = State.of("Kairo");
                    public final State<Integer> count = State.of(0);
                    public final Derived<String> greeting = Derived.derive(
                            () -> "Olá, " + name.get() + " (" + count.get() + ")"
                    );

                    public void increment() {
                        count.set(count.get() + 1);
                    }

                    @Override
                    public Map<String, Supplier<Object>> exposedBindings() {
                        return Map.of(
                                "name", () -> name,
                                "count", () -> count,
                                "greeting", () -> greeting
                        );
                    }

                    @Override
                    public Map<String, State<String>> twoWayStringStates() {
                        return Map.of("name", name);
                    }

                    @Override
                    public Map<String, Runnable> actions() {
                        return Map.of("increment", this::increment);
                    }
                }
                """.formatted(pkg)));

        created.add(write(root.resolve("src/main/java/" + packagePath + "/MainActivity.java"), """
                package %s;

                import android.app.Activity;
                import android.os.Bundle;
                import android.util.DisplayMetrics;
                import dev.kairo.components.api.ComponentRegistry;
                import dev.kairo.components.basic.BasicComponents;
                import dev.kairo.components.input.InputComponents;
                import dev.kairo.css.KairoCssParser;
                import dev.kairo.layout.LayoutConstraints;
                import dev.kairo.platform.android.AndroidPlatformHost;
                import dev.kairo.xml.KairoXmlParser;

                public final class MainActivity extends Activity {
                    private AndroidPlatformHost.MountedHost host;

                    @Override
                    protected void onCreate(Bundle savedInstanceState) {
                        super.onCreate(savedInstanceState);
                        ComponentRegistry registry = new ComponentRegistry();
                        BasicComponents.registerAll(registry);
                        InputComponents.registerAll(registry);

                        AndroidPlatformHost platform = new AndroidPlatformHost();
                        host = platform.bootstrap(this, registry);

                        String xml = host.resourceManager().resolveText("screens/main.xml").orElseThrow();
                        String css = host.resourceManager().resolveText("app.kcss").orElseThrow();

                        DisplayMetrics metrics = getResources().getDisplayMetrics();
                        host.runtime().mount(
                                new KairoXmlParser().parse("main.xml", xml).root(),
                                new KairoCssParser().parse(css),
                                new MainScreenModel(),
                                LayoutConstraints.fixed(metrics.widthPixels, metrics.heightPixels)
                        );
                        setContentView(host.renderer().rootContainerView());
                    }

                    @Override
                    protected void onDestroy() {
                        if (host != null) {
                            host.destroy();
                        }
                        super.onDestroy();
                    }
                }
                """.formatted(pkg)));

        created.add(write(root.resolve("src/test/java/" + packagePath + "/MainScreenTest.java"), """
                package %s;

                import dev.kairo.components.api.ComponentRegistry;
                import dev.kairo.components.basic.BasicComponents;
                import dev.kairo.components.input.InputComponents;
                import dev.kairo.css.KairoCssParser;
                import dev.kairo.events.CommonEvents;
                import dev.kairo.layout.LayoutConstraints;
                import dev.kairo.runtime.KairoRuntime;
                import dev.kairo.testkit.FakeRenderer;
                import dev.kairo.xml.KairoXmlParser;
                import org.junit.jupiter.api.Test;

                import java.nio.file.Files;
                import java.nio.file.Path;

                import static org.assertj.core.api.Assertions.assertThat;

                final class MainScreenTest {
                    @Test
                    void rendersAndUpdatesCountWithoutEmulator() throws Exception {
                        ComponentRegistry registry = new ComponentRegistry();
                        BasicComponents.registerAll(registry);
                        InputComponents.registerAll(registry);

                        FakeRenderer renderer = new FakeRenderer();
                        KairoRuntime runtime = new KairoRuntime(registry, renderer);
                        MainScreenModel model = new MainScreenModel();

                        String xml = Files.readString(Path.of("src/main/res/kairo/screens/main.xml"));
                        String css = Files.readString(Path.of("src/main/res/kairo/app.kcss"));

                        runtime.mount(
                                new KairoXmlParser().parse("main.xml", xml).root(),
                                new KairoCssParser().parse(css),
                                model,
                                LayoutConstraints.fixed(390, 844)
                        );
                        assertThat(renderer.findByLogicalId("headline").props.get("value")).isEqualTo("Olá, Kairo (0)");

                        renderer.dispatch(new CommonEvents.ClickEvent(renderer.findByLogicalId("actionBtn").id()));
                        assertThat(renderer.findByLogicalId("headline").props.get("value")).isEqualTo("Olá, Kairo (1)");
                        runtime.destroy();
                    }
                }
                """.formatted(pkg)));

        created.add(write(root.resolve("README.md"), """
                # %s (Kairo v1)

                Comandos rápidos:
                - `kairo doctor`
                - `kairo inspect`
                - `kairo test`
                - `kairo build`
                """.formatted(projectName)));

        return new ScaffoldResult(root, created);
    }

    private static Path write(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return Files.writeString(target, content, StandardCharsets.UTF_8);
    }
}
