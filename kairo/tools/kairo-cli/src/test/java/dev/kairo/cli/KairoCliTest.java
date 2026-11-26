package dev.kairo.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

final class KairoCliTest {

    @TempDir
    Path tempDir;

    @Test
    void scaffoldsCompleteProjectAndPassesOfflineInspect() throws Exception {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();

        int createCode = KairoCli.execute(
                new String[]{"create", "demo-app", "--dir", tempDir.toString(), "--package", "com.acme.demo"},
                new PrintStream(outBuf),
                new PrintStream(errBuf)
        );
        assertThat(createCode).isZero();
        Path projectRoot = tempDir.resolve("demo-app");
        assertThat(projectRoot.resolve("build.gradle.kts")).exists();
        assertThat(projectRoot.resolve("src/main/res/kairo/screens/main.xml")).exists();
        assertThat(projectRoot.resolve("src/main/res/kairo/app.kcss")).exists();
        assertThat(projectRoot.resolve("src/main/java/com/acme/demo/MainScreenModel.java")).exists();
        assertThat(projectRoot.resolve("src/test/java/com/acme/demo/MainScreenTest.java")).exists();

        outBuf.reset();
        errBuf.reset();
        int inspectCode = KairoCli.execute(
                new String[]{"inspect", projectRoot.toString()},
                new PrintStream(outBuf),
                new PrintStream(errBuf)
        );
        assertThat(inspectCode).isZero();
        assertThat(outBuf.toString(StandardCharsets.UTF_8)).contains("XML=1, KCSS=1, Diagnostics=0");
    }

    @Test
    void inspectReportsExactSemanticXmlAndWebCssErrors() throws Exception {
        KairoCli.execute(
                new String[]{"create", "broken-app", "--dir", tempDir.toString()},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())
        );
        Path projectRoot = tempDir.resolve("broken-app");
        Files.writeString(
                projectRoot.resolve("src/main/res/kairo/screens/main.xml"),
                "<Screen id=\"main\"><Div/></Screen>"
        );
        Files.writeString(
                projectRoot.resolve("src/main/res/kairo/app.kcss"),
                ".bad { float: left; }"
        );

        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int inspectCode = KairoCli.execute(
                new String[]{"inspect", projectRoot.toString()},
                new PrintStream(outBuf),
                new PrintStream(errBuf)
        );

        assertThat(inspectCode).isEqualTo(1);
        String errors = errBuf.toString(StandardCharsets.UTF_8);
        assertThat(errors).contains("SEM_UNKNOWN_COMPONENT");
        assertThat(errors).contains("STYLE_WEB_CSS_UNSUPPORTED");
    }

    @Test
    void doctorDetectsNeutralBoundaryViolationsAndCreateRejectsInvalidNames() throws Exception {
        int badCreate = KairoCli.execute(
                new String[]{"create", "../escape", "--dir", tempDir.toString()},
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream())
        );
        assertThat(badCreate).isEqualTo(1);

        Path badModel = tempDir.resolve("src/main/java/app/BadModel.java");
        Files.createDirectories(badModel.getParent());
        Files.writeString(badModel, "package app;\nimport android.widget.TextView;\npublic class BadModel {}");

        DoctorChecker.DoctorReport report = new DoctorChecker().run(tempDir);
        assertThat(report.checks().stream()
                .filter(c -> c.label().equals("Neutral Architecture Guard"))
                .findFirst()
                .orElseThrow()
                .ok()).isFalse();
    }
}
