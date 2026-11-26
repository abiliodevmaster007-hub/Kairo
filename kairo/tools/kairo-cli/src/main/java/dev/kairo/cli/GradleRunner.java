package dev.kairo.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Resolves and executes Gradle Wrapper & ADB commands for kairo build / test / clean / run.
 */
public final class GradleRunner {
    public enum Action {
        BUILD,
        TEST,
        CLEAN,
        RUN
    }

    public List<String> buildCommand(Action action, Path projectDir) {
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ROOT)
                .contains("win");
        Path wrapper = projectDir.resolve(windows ? "gradlew.bat" : "gradlew");
        String gradleBin = Files.exists(wrapper) ? wrapper.toAbsolutePath().toString() : "gradle";

        return switch (action) {
            case BUILD -> List.of(gradleBin, "assembleDebug");
            case TEST -> List.of(gradleBin, "test");
            case CLEAN -> List.of(gradleBin, "clean");
            case RUN -> List.of(gradleBin, "installDebug");
        };
    }

    public int execute(Action action, Path projectDir, PrintStream out, PrintStream err) {
        List<String> command = buildCommand(action, projectDir);
        out.println("-> Executing: " + String.join(" ", command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command)
                    .directory(projectDir.toFile())
                    .inheritIO();
            Process process = pb.start();
            int exit = process.waitFor();
            if (exit != 0) {
                err.println("Gradle failed with exit code " + exit);
            }
            return exit;
        } catch (IOException ex) {
            err.println("Failed to launch Gradle (" + command.get(0) + "): " + ex.getMessage());
            return 1;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            err.println("Gradle execution interrupted");
            return 1;
        }
    }
}
