package dev.kairo.testkit;

import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.renderer.api.RenderHandle;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * In-memory FakeRenderer for JVM unit/integration tests without Android Emulator.
 */
public final class FakeRenderer implements Renderer {
    public static final class FakeNode implements RenderHandle {
        private final KairoId id;
        private final String componentType;
        public final Map<String, String> props = new LinkedHashMap<>();
        public final List<KairoId> childOrder = new ArrayList<>();
        public ComputedStyle style;
        public LayoutBounds bounds = LayoutBounds.ZERO;
        public Set<VisualState> visualStates = Set.of();

        FakeNode(KairoId id, String componentType, Map<String, String> props, ComputedStyle style) {
            this.id = id;
            this.componentType = componentType;
            this.props.putAll(props);
            this.style = style;
        }

        @Override public KairoId id() { return id; }
        @Override public String componentType() { return componentType; }
    }

    private final Map<KairoId, FakeNode> nodes = new LinkedHashMap<>();
    private final List<RenderPatch> appliedPatches = new ArrayList<>();
    private Consumer<UIEvent> eventSink = ignored -> {};

    @Override
    public Capabilities capabilities() {
        return new Capabilities(true, true, false, "fake-testkit");
    }

    @Override
    public LayoutEngine.LeafMeasurer leafMeasurer() {
        return (node, constraints) -> new LayoutEngine.MeasuredSize(
                Math.min(constraints.maxWidth(), 180),
                "TextArea".equals(node.componentType()) ? 120 : 44
        );
    }

    @Override
    public RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink) {
        this.eventSink = eventSink != null ? eventSink : ignored -> {};
        nodes.clear();
        FakeNode root = new FakeNode(rootId, componentType, Map.of(), ComputedStyle.empty());
        nodes.put(rootId, root);
        return root;
    }

    @Override
    public void apply(List<RenderPatch> patchBatch) {
        appliedPatches.addAll(patchBatch);
        for (RenderPatch patch : patchBatch) {
            if (patch instanceof RenderPatch.CreateNode create) {
                nodes.put(create.id(), new FakeNode(create.id(), create.componentType(), create.props(), create.style()));
            } else if (patch instanceof RenderPatch.UpdateProp prop) {
                FakeNode node = nodes.get(prop.id());
                if (node != null) {
                    if (prop.value() == null) {
                        node.props.remove(prop.property());
                    } else {
                        node.props.put(prop.property(), prop.value());
                    }
                }
            } else if (patch instanceof RenderPatch.UpdateStyle stylePatch) {
                FakeNode node = nodes.get(stylePatch.id());
                if (node != null) node.style = stylePatch.style();
            } else if (patch instanceof RenderPatch.UpdateVisualStates vsPatch) {
                FakeNode node = nodes.get(vsPatch.id());
                if (node != null) node.visualStates = Set.copyOf(vsPatch.states());
            } else if (patch instanceof RenderPatch.UpdateBounds boundsPatch) {
                FakeNode node = nodes.get(boundsPatch.id());
                if (node != null) node.bounds = boundsPatch.bounds();
            } else if (patch instanceof RenderPatch.InsertChild insert) {
                FakeNode parent = nodes.get(insert.parentId());
                if (parent != null) {
                    parent.childOrder.remove(insert.childId());
                    int idx = Math.max(0, Math.min(insert.index(), parent.childOrder.size()));
                    parent.childOrder.add(idx, insert.childId());
                }
            } else if (patch instanceof RenderPatch.MoveChild move) {
                FakeNode parent = nodes.get(move.parentId());
                if (parent != null) {
                    parent.childOrder.remove(move.childId());
                    int idx = Math.max(0, Math.min(move.toIndex(), parent.childOrder.size()));
                    parent.childOrder.add(idx, move.childId());
                }
            } else if (patch instanceof RenderPatch.RemoveNode remove) {
                removeSubtree(remove.id());
            }
        }
    }

    private void removeSubtree(KairoId id) {
        FakeNode removed = nodes.remove(id);
        if (removed != null) {
            for (KairoId childId : List.copyOf(removed.childOrder)) {
                removeSubtree(childId);
            }
            removed.childOrder.clear();
        }
        for (FakeNode node : nodes.values()) {
            node.childOrder.remove(id);
        }
    }

    @Override
    public void unmountRoot(KairoId rootId) {
        nodes.clear();
    }

    @Override
    public Set<KairoId> mountedIds() {
        return Set.copyOf(nodes.keySet());
    }

    public FakeNode findById(KairoId id) {
        return nodes.get(id);
    }

    public FakeNode findByLogicalId(String logicalId) {
        return nodes.values().stream()
                .filter(n -> logicalId.equals(n.id().logicalId()))
                .findFirst()
                .orElse(null);
    }

    public void dispatch(UIEvent event) {
        eventSink.accept(event);
    }

    public List<RenderPatch> appliedPatches() {
        return List.copyOf(appliedPatches);
    }
}
