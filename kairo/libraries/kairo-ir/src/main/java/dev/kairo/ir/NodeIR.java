package dev.kairo.ir;

import dev.kairo.core.SourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalized Intermediate Representation of a declarative UI node.
 */
public final class NodeIR {
    private final String componentType;
    private final String logicalId;
    private final String stableKeyExpr;
    private final List<String> styleClasses;
    private final Map<String, String> properties;
    private final Map<String, BindingSpec> bindings;
    private final Map<String, String> eventHandlers;
    private final String slotName;
    private final List<NodeIR> children;
    private final SourceLocation sourceLocation;

    public NodeIR(
            String componentType,
            String logicalId,
            String stableKeyExpr,
            List<String> styleClasses,
            Map<String, String> properties,
            Map<String, BindingSpec> bindings,
            Map<String, String> eventHandlers,
            String slotName,
            List<NodeIR> children,
            SourceLocation sourceLocation) {
        this.componentType = componentType;
        this.logicalId = logicalId;
        this.stableKeyExpr = stableKeyExpr;
        this.styleClasses = List.copyOf(styleClasses);
        this.properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        this.bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
        this.eventHandlers = Collections.unmodifiableMap(new LinkedHashMap<>(eventHandlers));
        this.slotName = slotName;
        this.children = List.copyOf(children);
        this.sourceLocation = sourceLocation == null ? SourceLocation.UNKNOWN : sourceLocation;
    }

    public String componentType() { return componentType; }
    public String logicalId() { return logicalId; }
    public String stableKeyExpr() { return stableKeyExpr; }
    public List<String> styleClasses() { return styleClasses; }
    public Map<String, String> properties() { return properties; }
    public Map<String, BindingSpec> bindings() { return bindings; }
    public Map<String, String> eventHandlers() { return eventHandlers; }
    public String slotName() { return slotName; }
    public List<NodeIR> children() { return children; }
    public SourceLocation sourceLocation() { return sourceLocation; }
}
