package dev.kairo.components.api;

import dev.kairo.core.SourceLocation;
import dev.kairo.ir.NodeIR;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neutral slot extraction and template projection utilities for custom composable components.
 */
public final class SlotProjection {
    public static final String DEFAULT_SLOT = "default";

    private SlotProjection() {}

    /**
     * Extracts named slots from a custom component invocation into single root NodeIR per slot.
     * Multiple unslotted children are wrapped into a neutral Column container for the "default" slot.
     */
    public static Map<String, NodeIR> extractSlots(NodeIR invocation) {
        Map<String, List<NodeIR>> grouped = extractAllSlots(invocation);
        Map<String, NodeIR> collapsed = new LinkedHashMap<>();
        grouped.forEach((slotName, children) -> {
            if (children.size() == 1) {
                collapsed.put(slotName, children.get(0));
            } else if (!children.isEmpty()) {
                collapsed.put(slotName, new NodeIR(
                        "Column",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        slotName,
                        children,
                        invocation.sourceLocation()
                ));
            }
        });
        return collapsed;
    }

    public static Map<String, List<NodeIR>> extractAllSlots(NodeIR invocation) {
        Map<String, List<NodeIR>> slots = new LinkedHashMap<>();
        if (invocation == null) return slots;
        for (NodeIR child : invocation.children()) {
            if ("slot".equals(child.componentType())) {
                String name = child.slotName() != null ? child.slotName() : child.properties().getOrDefault("name", DEFAULT_SLOT);
                slots.computeIfAbsent(name, ignored -> new ArrayList<>()).addAll(child.children());
            } else {
                String name = (child.slotName() != null && !child.slotName().isBlank())
                        ? child.slotName()
                        : child.properties().getOrDefault("slot", DEFAULT_SLOT);
                slots.computeIfAbsent(name, ignored -> new ArrayList<>()).add(child);
            }
        }
        return slots;
    }

    /**
     * Replaces any &lt;slot name="..."/&gt; placeholders inside an expanded component template with caller-supplied nodes.
     */
    public static NodeIR projectSlots(NodeIR templateNode, Map<String, NodeIR> namedSlots) {
        if (templateNode == null) return null;
        if ("slot".equals(templateNode.componentType())) {
            String slotName = templateNode.slotName() != null
                    ? templateNode.slotName()
                    : templateNode.properties().getOrDefault("name", DEFAULT_SLOT);
            NodeIR provided = namedSlots != null ? namedSlots.get(slotName) : null;
            if (provided != null) {
                return provided;
            }
            // Fallback content inside <slot>...</slot> if caller omitted that slot
            if (templateNode.children().size() == 1) {
                return projectSlots(templateNode.children().get(0), namedSlots);
            }
            if (!templateNode.children().isEmpty()) {
                return new NodeIR(
                        "Column",
                        null,
                        null,
                        List.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        null,
                        templateNode.children().stream().map(c -> projectSlots(c, namedSlots)).toList(),
                        SourceLocation.UNKNOWN
                );
            }
            return null;
        }

        List<NodeIR> projectedChildren = new ArrayList<>();
        for (NodeIR child : templateNode.children()) {
            NodeIR projected = projectSlots(child, namedSlots);
            if (projected != null) {
                projectedChildren.add(projected);
            }
        }
        return new NodeIR(
                templateNode.componentType(),
                templateNode.logicalId(),
                templateNode.stableKeyExpr(),
                templateNode.styleClasses(),
                templateNode.properties(),
                templateNode.bindings(),
                templateNode.eventHandlers(),
                templateNode.slotName(),
                projectedChildren,
                templateNode.sourceLocation()
        );
    }
}
