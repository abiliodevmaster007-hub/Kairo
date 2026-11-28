package dev.kairo.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Resolves and executes Gradle Wrapper & Android APK packaging commands for kairo apk / build / test / clean / run.
 */
public final class GradleRunner {
    public enum Action {
        APK,
        APK_RELEASE,
        BUILD,
        TEST,
        CLEAN,
        RUN
    }

    public record ExecutionPlan(Path workingDirectory, List<String> command, boolean androidPackagingTarget) {}

    public ExecutionPlan resolvePlan(Action action, Path projectDir) {
        Path normalized = projectDir.toAbsolutePath().normalize();
        Path androidShell = findAndroidPackagingShell(normalized);

        boolean targetsApkModule = switch (action) {
            case APK, APK_RELEASE, BUILD, RUN -> androidShell != null;
            case TEST, CLEAN -> false;
        };

        Path workingDir = targetsApkModule ? androidShell : normalized;
        boolean androidTarget = targetsApkModule || isAndroidApplicationProject(workingDir);
        String gradleBin = resolveGradleExecutable(workingDir, normalized);

        String task = switch (action) {
            case APK, BUILD -> "assembleDebug";
            case APK_RELEASE -> "assembleRelease";
            case TEST -> "test";
            case CLEAN -> "clean";
            case RUN -> "installDebug";
        };

        return new ExecutionPlan(workingDir, List.of(gradleBin, task, "--no-daemon"), androidTarget);
    }

    public List<String> buildCommand(Action action, Path projectDir) {
        return resolvePlan(action, projectDir).command();
    }

    public int execute(Action action, Path projectDir, PrintStream out, PrintStream err) {
        ExecutionPlan plan = resolvePlan(action, projectDir);
        if ((action == Action.APK || action == Action.APK_RELEASE) && !plan.androidPackagingTarget()) {
            err.println("No Android packaging module (packaging/kairo-android-apk or com.android.application) found in: "
                    + projectDir.toAbsolutePath().normalize());
            return 1;
        }
        if (plan.androidPackagingTarget() && !hasAndroidSdkConfigured(plan.workingDirectory())) {
            err.println("Android SDK not configured. Set ANDROID_HOME or ANDROID_SDK_ROOT (Android Studio is not required).");
            return 1;
        }

        out.println("-> Working directory: " + plan.workingDirectory());
        out.println("-> Executing: " + String.join(" ", plan.command()));
        try {
            ProcessBuilder pb = new ProcessBuilder(plan.command())
                    .directory(plan.workingDirectory().toFile())
                    .inheritIO();
            Process process = pb.start();
            int exit = process.waitFor();
            if (exit != 0) {
                err.println("Gradle failed with exit code " + exit);
                return exit;
            }
            if (action == Action.APK || action == Action.APK_RELEASE || action == Action.BUILD) {
                reportGeneratedApks(plan.workingDirectory(), out);
            }
            return 0;
        } catch (IOException ex) {
            err.println("Failed to launch Gradle (" + plan.command().get(0) + "): " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("Gradle execution interrupted");
            return 1;
        }
    }

    static Path findAndroidPackagingShell(Path startDir) {
        if (isAndroidApplicationProject(startDir)) {
            return startDir;
        }
        Path current = startDir;
        for (int depth = 0; depth < 4 && current != null; depth++) {
            Path directShell = current.resolve("packaging/kairo-android-apk");
            if (Files.isRegularFile(directShell.resolve("build.gradle.kts"))) {
                return directShell;
            }
            Path nestedShell = current.resolve("kairo/packaging/kairo-android-apk");
            if (Files.isRegularFile(nestedShell.resolve("build.gradle.kts"))) {
                return nestedShell;
            }
            if (depth == 0 && Files.isRegularFile(current.resolve("build.gradle.kts"))
                    && !current.getFileName().toString().equals("kairo-cli")) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    static boolean isAndroidApplicationProject(Path dir) {
        Path buildScript = dir.resolve("build.gradle.kts");
        if (!Files.isRegularFile(buildScript)) {
            return false;
        }
        try {
            return Files.readString(buildScript, StandardCharsets.UTF_8).contains("com.android.application");
        } catch (IOException ignored) {
            return false;
        }
    }

    static boolean hasAndroidSdkConfigured(Path workingDir) {
        String androidHome = System.getenv("ANDROID_HOME");
        if (androidHome != null && !androidHome.isBlank() && Files.isDirectory(Path.of(androidHome))) {
            return true;
        }
        String sdkRoot = System.getenv("ANDROID_SDK_ROOT");
        if (sdkRoot != null && !sdkRoot.isBlank() && Files.isDirectory(Path.of(sdkRoot))) {
            return true;
        }
        return Files.isRegularFile(workingDir.resolve("local.properties"));
    }

    private static String resolveGradleExecutable(Path workingDir, Path requestedDir) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        String wrapperName = windows ? "gradlew.bat" : "gradlew";
        List<Path> candidates = List.of(
                workingDir.resolve(wrapperName),
                requestedDir.resolve(wrapperName),
                requestedDir.resolve("kairo").resolve(wrapperName),
                workingDir.resolve("../..").normalize().resolve(wrapperName)
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate) && (windows || Files.isExecutable(candidate))) {
                return candidate.toAbsolutePath().normalize().toString();
            }
        }
        return "gradle";
    }

    private static void reportGeneratedApks(Path workingDir, PrintStream out) {
        Path apkDir = workingDir.resolve("build/outputs/apk");
        if (!Files.isDirectory(apkDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(apkDir)) {
            walk.filter(p -> p.toString().endsWith(".apk"))
                    .forEach(apk -> out.println("APK ready: " + apk.toAbsolutePath().normalize()));
        } catch (IOException ignored) {}
    }
}
