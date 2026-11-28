package dev.kairo.cli;

import dev.kairo.core.Diagnostic;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Kairo CLI orchestrating project scaffolding (create), toolchain/architecture verification (doctor),
 * declarative XML/.kcss static validation (inspect), and Gradle/ADB build lifecycle (build, run, clean, test).
 */
public final class KairoCli {
    private static final String VERSION = "kairo-cli 0.1.0-v1";

    private KairoCli() {}

    public static void main(String[] args) {
        int code = execute(args, System.out, System.err);
        if (code != 0) {
            System.exit(code);
        }
    }

    public static int execute(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            printHelp(out);
            return 0;
        }
        String command = args[0];
        Path workDir = resolveOptionPath(args, "--dir", Path.of(".").toAbsolutePath().normalize());

        try {
            return switch (command) {
                case "create" -> handleCreate(args, workDir, out, err);
                case "doctor" -> handleDoctor(workDir, out);
                case "inspect" -> handleInspect(args, workDir, out, err);
                case "apk" -> handleApk(args, workDir, out, err);
                case "build" -> new GradleRunner().execute(GradleRunner.Action.BUILD, workDir, out, err);
                case "run" -> new GradleRunner().execute(GradleRunner.Action.RUN, workDir, out, err);
                case "clean" -> new GradleRunner().execute(GradleRunner.Action.CLEAN, workDir, out, err);
                case "test" -> new GradleRunner().execute(GradleRunner.Action.TEST, workDir, out, err);
                case "--version", "-v" -> {
                    out.println(VERSION);
                    yield 0;
                }
                default -> {
                    err.println("Unknown command: " + command);
                    printHelp(out);
                    yield 1;
                }
            };
        } catch (Exception ex) {
            err.println("Error: " + ex.getMessage());
            return 1;
        }
    }

    private static int handleApk(String[] args, Path workDir, PrintStream out, PrintStream err) {
        boolean release = hasFlag(args, "--release");
        GradleRunner.Action action = release ? GradleRunner.Action.APK_RELEASE : GradleRunner.Action.APK;
        return new GradleRunner().execute(action, workDir, out, err);
    }

    private static int handleCreate(String[] args, Path parentDir, PrintStream out, PrintStream err)
            throws IOException {
        if (args.length < 2 || args[1].startsWith("--")) {
            err.println("Usage: kairo create <project-name> [--dir <parent>] [--package <pkg>]");
            return 1;
        }
        String name = args[1];
        String pkg = resolveOptionValue(args, "--package");
        ProjectScaffolder.ScaffoldResult result = new ProjectScaffolder().create(name, parentDir, pkg);
        out.println("Created Kairo v1 project at: " + result.projectRoot());
        out.println("Files generated: " + result.createdFiles().size());
        return 0;
    }

    private static int handleDoctor(Path projectDir, PrintStream out) {
        DoctorChecker.DoctorReport report = new DoctorChecker().run(projectDir);
        for (DoctorChecker.CheckResult check : report.checks()) {
            String tag = check.ok() ? "[OK]   " : "[WARN] ";
            out.println(tag + check.label() + ": " + check.detail());
        }
        return 0;
    }

    private static int handleInspect(String[] args, Path defaultDir, PrintStream out, PrintStream err)
            throws IOException {
        Path target = defaultDir;
        if (args.length >= 2 && !args[1].startsWith("--")) {
            target = Path.of(args[1]).toAbsolutePath().normalize();
        }
        ProjectInspector.InspectionReport report = new ProjectInspector().inspectProject(target);
        out.println("Inspected XML=" + report.xmlFilesChecked()
                + ", KCSS=" + report.styleFilesChecked()
                + ", Diagnostics=" + report.diagnostics().size());
        for (Diagnostic d : report.diagnostics()) {
            (d.severity() == Diagnostic.Severity.ERROR ? err : out).println("  " + d);
        }
        return report.hasErrors() ? 1 : 0;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveOptionValue(String[] args, String flag) {
        for (int i = 0; i < args.length - 1; i++) {
            if (flag.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    private static Path resolveOptionPath(String[] args, String flag, Path fallback) {
        String raw = resolveOptionValue(args, flag);
        return raw != null ? Path.of(raw).toAbsolutePath().normalize() : fallback;
    }

    private static void printHelp(PrintStream out) {
        out.println("""
                Kairo CLI v0.1 (Declarative Java UI for Android)
                Comandos:
                  kairo create <nome> [--dir <pasta>] [--package <pkg>]   Criar projeto Kairo v1 completo
                  kairo doctor [--dir <pasta>]                            Verificar Java 17+, Gradle, SDK e ArchGuard
                  kairo inspect [pasta]                                   Validar XML, IR semântico e dialeto .kcss
                  kairo apk [--dir <pasta>] [--release]                   Empacotar APK Android headless (sem Android Studio)
                  kairo build [--dir <pasta>]                             Compilar APK via assembleDebug
                  kairo run [--dir <pasta>]                               Instalar no emulador/dispositivo
                  kairo clean [--dir <pasta>]                             Limpar build Gradle
                  kairo test [--dir <pasta>]                              Correr suites JVM + ArchUnit
                """);
    }
}
