package dev.kairo.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Verifies local toolchain readiness (Java 17+, Gradle Wrapper, Android SDK) and
 * checks neutral Java sources against forbidden platform toolkit imports.
 */
public final class DoctorChecker {
    public record CheckResult(String label, boolean ok, String detail) {}

    public record DoctorReport(List<CheckResult> checks) {
        public boolean isHealthy() {
            return checks.stream().allMatch(CheckResult::ok);
        }
    }

    public DoctorReport run(Path projectDir) {
        List<CheckResult> checks = new ArrayList<>();

        int javaFeature = Runtime.version().feature();
        checks.add(new CheckResult(
                "Java Runtime",
                javaFeature >= 17,
                System.getProperty("java.version") + " (baseline: 17+)"
        ));

        boolean hasGradlew = Files.exists(projectDir.resolve("gradlew"))
                || Files.exists(projectDir.resolve("gradlew.bat"));
        checks.add(new CheckResult(
                "Gradle Wrapper",
                hasGradlew,
                hasGradlew ? "gradlew found in " + projectDir.getFileName() : "missing gradlew in " + projectDir
        ));

        String androidSdk = System.getenv("ANDROID_HOME");
        if (androidSdk == null || androidSdk.isBlank()) {
            androidSdk = System.getenv("ANDROID_SDK_ROOT");
        }
        boolean hasSdk = androidSdk != null && !androidSdk.isBlank() && Files.isDirectory(Path.of(androidSdk));
        checks.add(new CheckResult(
                "Android SDK",
                hasSdk,
                hasSdk ? androidSdk : "Set ANDROID_HOME or ANDROID_SDK_ROOT for APK packaging"
        ));

        List<String> boundaryViolations = scanNeutralImports(projectDir);
        checks.add(new CheckResult(
                "Neutral Architecture Guard",
                boundaryViolations.isEmpty(),
                boundaryViolations.isEmpty()
                        ? "Zero forbidden platform imports in neutral sources"
                        : "Violations: " + String.join(", ", boundaryViolations)
        ));

        return new DoctorReport(checks);
    }

    static List<String> scanNeutralImports(Path projectDir) {
        Path srcDir = projectDir.resolve("src");
        Path librariesDir = projectDir.resolve("libraries");
        List<String> violations = new ArrayList<>();

        scanDirectory(librariesDir, violations);
        if (Files.isDirectory(srcDir)) {
            scanDirectory(srcDir, violations);
        }
        return violations;
    }

    private static void scanDirectory(Path root, List<String> violations) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
                String pathStr = file.toString().replace('\\', '/');
                if (pathStr.contains("renderer-android")
                        || pathStr.contains("platform-android")
                        || pathStr.contains("/android/")) {
                    return;
                }
                try {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    for (String line : content.split("\n")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("import android.")
                                || trimmed.startsWith("import androidx.")
                                || trimmed.startsWith("import javafx.")
                                || trimmed.startsWith("import javax.swing.")
                                || trimmed.startsWith("import org.reflections.")) {
                            violations.add(root.relativize(file) + " -> " + trimmed);
                        }
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
