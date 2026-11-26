package dev.kairo.components.basic;

import dev.kairo.core.SourceLocation;
import dev.kairo.ir.BindingSpec;
import dev.kairo.ir.NodeIR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Type-safe, platform-neutral fluent Java DSL producing NodeIR trees for the 12 basic Kairo components.
 * Can be composed directly in Java or mixed with XML templates and custom components.
 */
public final class BasicUI {
    private BasicUI() {}

    public static NodeBuilder node(String componentType) {
        return new NodeBuilder(componentType);
    }

    public static NodeBuilder screen(String id) {
        return new NodeBuilder("Screen").id(id);
    }

    public static NodeBuilder column() {
        return new NodeBuilder("Column");
    }

    public static NodeBuilder row() {
        return new NodeBuilder("Row");
    }

    public static NodeBuilder box() {
        return new NodeBuilder("Box");
    }

    public static NodeBuilder stack() {
        return new NodeBuilder("Stack");
    }

    public static NodeBuilder scroll() {
        return new NodeBuilder("Scroll");
    }

    public static NodeBuilder text(String value) {
        return new NodeBuilder("Text").prop("value", value);
    }

    public static NodeBuilder button(String text) {
        return new NodeBuilder("Button").prop("text", text);
    }

    public static NodeBuilder image(String src, String alt) {
        return new NodeBuilder("Image").prop("src", src).prop("alt", alt);
    }

    public static NodeBuilder card() {
        return new NodeBuilder("Card");
    }

    public static NodeBuilder divider() {
        return new NodeBuilder("Divider");
    }

    public static NodeBuilder progress() {
        return new NodeBuilder("Progress");
    }

    public static NodeIR slotPlaceholder(String name, NodeIR... fallbackChildren) {
        return new NodeBuilder("slot")
                .prop("name", name)
                .slot(name)
                .children(fallbackChildren)
                .build();
    }

    public static final class NodeBuilder {
        private final String componentType;
        private String logicalId;
        private String stableKeyExpr;
        private String slotName;
        private final List<String> styleClasses = new ArrayList<>();
        private final Map<String, String> properties = new LinkedHashMap<>();
        private final Map<String, BindingSpec> bindings = new LinkedHashMap<>();
        private final Map<String, String> eventHandlers = new LinkedHashMap<>();
        private final List<NodeIR> children = new ArrayList<>();
        private SourceLocation sourceLocation = SourceLocation.UNKNOWN;

        private NodeBuilder(String componentType) {
            this.componentType = componentType;
        }

        public NodeBuilder id(String logicalId) {
            this.logicalId = logicalId;
            return this;
        }

        public NodeBuilder key(String stableKeyExpr) {
            this.stableKeyExpr = stableKeyExpr;
            return this;
        }

        public NodeBuilder slot(String slotName) {
            this.slotName = slotName;
            return this;
        }

        public NodeBuilder classes(String... classes) {
            this.styleClasses.addAll(Arrays.asList(classes));
            return this;
        }

        public NodeBuilder prop(String name, String value) {
            if (value != null) {
                this.properties.put(name, value);
            }
            return this;
        }

        public NodeBuilder bind(String prop, String expr) {
            this.bindings.put(prop, new BindingSpec(prop, expr, false));
            return this;
        }

        public NodeBuilder bindTwoWay(String prop, String expr) {
            this.bindings.put(prop, new BindingSpec(prop, expr, true));
            return this;
        }

        public NodeBuilder on(String eventName, String actionName) {
            this.eventHandlers.put(eventName, actionName);
            return this;
        }

        public NodeBuilder child(NodeIR child) {
            if (child != null) this.children.add(child);
            return this;
        }

        public NodeBuilder child(NodeBuilder childBuilder) {
            if (childBuilder != null) this.children.add(childBuilder.build());
            return this;
        }

        public NodeBuilder children(NodeIR... nodes) {
            for (NodeIR n : nodes) {
                if (n != null) this.children.add(n);
            }
            return this;
        }

        public NodeBuilder location(SourceLocation location) {
            this.sourceLocation = location;
            return this;
        }

        public NodeIR build() {
            return new NodeIR(
                    componentType,
                    logicalId,
                    stableKeyExpr,
                    styleClasses,
                    properties,
                    bindings,
                    eventHandlers,
                    slotName,
                    children,
                    sourceLocation
            );
        }
    }
}
