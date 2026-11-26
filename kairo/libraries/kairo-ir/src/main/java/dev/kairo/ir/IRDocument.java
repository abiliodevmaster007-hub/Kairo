package dev.kairo.ir;

import dev.kairo.core.Diagnostic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level Intermediate Representation document produced by KairoXmlParser.
 */
public final class IRDocument {
    private final String sourceFile;
    private final NodeIR root;
    private final List<String> styleImports;
    private final Map<String, String> componentImports;
    private final Diagnostic.Bag diagnostics;

    public IRDocument(
            String sourceFile,
            NodeIR root,
            List<String> styleImports,
            Map<String, String> componentImports,
            Diagnostic.Bag diagnostics) {
        this.sourceFile = sourceFile;
        this.root = root;
        this.styleImports = List.copyOf(styleImports);
        this.componentImports = Collections.unmodifiableMap(new LinkedHashMap<>(componentImports));
        this.diagnostics = diagnostics;
    }

    public String sourceFile() { return sourceFile; }
    public NodeIR root() { return root; }
    public List<String> styleImports() { return styleImports; }
    public Map<String, String> componentImports() { return componentImports; }
    public Diagnostic.Bag diagnostics() { return diagnostics; }
}
