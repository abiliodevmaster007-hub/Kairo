package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Virtual UI node owned by KairoRuntime, independent of native backend handles.
 */
public final class VirtualNode {
    private final KairoId id;
    private final String componentType;
    private final String logicalId;
    private final StableKey stableKey;
    private final Map<String, String> props;
    private final ComputedStyle style;
    private final Set<VisualState> visualStates;
    private final Map<String, String> eventHandlers;
    private final List<VirtualNode> children;
    private LayoutBounds bounds = LayoutBounds.ZERO;

    public VirtualNode(
            KairoId id,
            String componentType,
            String logicalId,
            StableKey stableKey,
            Map<String, String> props,
            ComputedStyle style,
            Set<VisualState> visualStates,
            Map<String, String> eventHandlers,
            List<VirtualNode> children) {
        this.id = id;
        this.componentType = componentType;
        this.logicalId = logicalId;
        this.stableKey = stableKey;
        this.props = Map.copyOf(props);
        this.style = style;
        this.visualStates = Set.copyOf(visualStates);
        this.eventHandlers = Map.copyOf(eventHandlers);
        this.children = List.copyOf(children);
    }

    public KairoId id() { return id; }
    public String componentType() { return componentType; }
    public String logicalId() { return logicalId; }
    public StableKey stableKey() { return stableKey; }
    public Map<String, String> props() { return props; }
    public ComputedStyle style() { return style; }
    public Set<VisualState> visualStates() { return visualStates; }
    public Map<String, String> eventHandlers() { return eventHandlers; }
    public List<VirtualNode> children() { return children; }

    public LayoutBounds bounds() { return bounds; }
    public void setBounds(LayoutBounds bounds) { this.bounds = bounds; }
}
