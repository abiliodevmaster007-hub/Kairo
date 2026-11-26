package dev.kairo.cli;

import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.api.SemanticIRValidator;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.form.FormController;
import dev.kairo.components.input.InputComponents;
import dev.kairo.components.list.KairoListDiff;
import dev.kairo.core.Diagnostic;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.IRValidator;
import dev.kairo.navigation.Router;
import dev.kairo.xml.KairoXmlParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Validates Kairo declarative XML screens and .kcss stylesheets offline across a project,
 * surfacing Diagnostics with exact file:line:column locations.
 */
public final class ProjectInspector {
    public record InspectionReport(
            int xmlFilesChecked,
            int styleFilesChecked,
            List<Diagnostic> diagnostics
    ) {
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
        }
    }

    private final ComponentRegistry registry;

    public ProjectInspector() {
        this.registry = createDefaultRegistry();
    }

    public static ComponentRegistry createDefaultRegistry() {
        ComponentRegistry reg = new ComponentRegistry();
        BasicComponents.registerAll(reg);
        InputComponents.registerAll(reg);
        FormController.registerAll(reg);
        KairoListDiff.registerAll(reg);
        Router.registerAll(reg);
        return reg;
    }

    public InspectionReport inspectProject(Path projectDir) throws IOException {
        List<Diagnostic> allDiagnostics = new ArrayList<>();
        KairoXmlParser xmlParser = new KairoXmlParser();
        IRValidator irValidator = new IRValidator();
        SemanticIRValidator semanticValidator = new SemanticIRValidator();
        KairoCssParser cssParser = new KairoCssParser();

        int xmlCount = 0;
        int cssCount = 0;

        Path searchRoot = Files.isDirectory(projectDir.resolve("src"))
                ? projectDir.resolve("src")
                : projectDir;

        try (Stream<Path> walk = Files.walk(searchRoot)) {
            List<Path> files = walk.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                String pathStr = file.toString().replace('\\', '/');
                if (pathStr.contains("/build/") || pathStr.contains("/.gradle/")) {
                    continue;
                }
                if (pathStr.endsWith(".xml") && (pathStr.contains("/kairo/") || pathStr.contains("\\kairo\\"))) {
                    xmlCount++;
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    IRDocument doc = xmlParser.parseDocument(projectDir.relativize(file).toString(), content);
                    Diagnostic.Bag irBag = irValidator.validate(doc);
                    Diagnostic.Bag semBag = semanticValidator.validate(doc, registry);
                    allDiagnostics.addAll(irBag.all());
                    semBag.all().forEach(d -> {
                        if (!allDiagnostics.contains(d)) {
                            allDiagnostics.add(d);
                        }
                    });
                } else if (pathStr.endsWith(".kcss")
                        || (pathStr.endsWith(".css") && pathStr.contains("/kairo/"))) {
                    cssCount++;
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    CssStylesheet parsed = cssParser.parseWithDiagnostics(
                            projectDir.relativize(file).toString(),
                            content
                    );
                    allDiagnostics.addAll(parsed.diagnostics().all());
                }
            }
        }
        return new InspectionReport(xmlCount, cssCount, List.copyOf(allDiagnostics));
    }
}
