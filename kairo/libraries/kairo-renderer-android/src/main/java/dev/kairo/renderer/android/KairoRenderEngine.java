package dev.kairo.renderer.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.renderer.api.RenderEngineStats;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.resources.ResourceManager;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.VisualState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Modular Android rendering engine coordinating:
 * - KairoLayoutHostGroup positioning strictly from kairo-layout bounds
 * - Surface & typography styling via KairoStyleApplier
 * - IME/cursor protection via KairoImeBridge & Accessibility via KairoAccessibilityBridge
 * - Optional C++17 native scene-graph & SDF rasterizer via KairoNativeEngine
 */
public final class KairoRenderEngine {
    static final class NodeRecord {
        final KairoId id;
        final String componentType;
        final View view;
        final Map<String, String> props = new LinkedHashMap<>();
        final List<KairoId> childIds = new ArrayList<>();
        ComputedStyle style = ComputedStyle.empty();
        LayoutBounds bounds = LayoutBounds.ZERO;
        Set<VisualState> visualStates = Set.of();

        NodeRecord(KairoId id, String componentType, View view) {
            this.id = id;
            this.componentType = componentType;
            this.view = view;
        }
    }

    private final KairoLayoutHostGroup rootContainer;
    private final KairoImeBridge imeBridge = new KairoImeBridge();
    private final KairoViewFactory viewFactory;
    private final KairoNativeEngine nativeEngine = new KairoNativeEngine();

    private final Map<KairoId, NodeRecord> nodes = new LinkedHashMap<>();
    private final Map<View, LayoutBounds> pendingBounds = new LinkedHashMap<>();
    private Consumer<UIEvent> eventSink = ignored -> {};

    private long totalPatchesApplied;
    private int lastPatchBatchSize;
    private long lastFrameDurationMicros;

    public KairoRenderEngine(Context context, ResourceManager resourceManager) {
        this.rootContainer = new KairoLayoutHostGroup(context);
        this.viewFactory = new KairoViewFactory(context, imeBridge, resourceManager);
    }

    public KairoLayoutHostGroup rootContainerView() {
        return rootContainer;
    }

    public KairoImeBridge imeBridge() {
        return imeBridge;
    }

    public KairoNativeEngine nativeEngine() {
        return nativeEngine;
    }

    public boolean isNativeAccelerated() {
        return nativeEngine.isActive();
    }

    public LayoutEngine.LeafMeasurer leafMeasurer() {
        return (node, constraints) -> {
            NodeRecord existing = nodes.get(node.id());
            View view = existing != null
                    ? existing.view
                    : viewFactory.createView(node.id(), node.componentType(), eventSink);
            int wSpec = Double.isFinite(constraints.maxWidth())
                    ? View.MeasureSpec.makeMeasureSpec(Math.max(0, (int) constraints.maxWidth()), View.MeasureSpec.AT_MOST)
                    : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int hSpec = Double.isFinite(constraints.maxHeight())
                    ? View.MeasureSpec.makeMeasureSpec(Math.max(0, (int) constraints.maxHeight()), View.MeasureSpec.AT_MOST)
                    : View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            view.measure(wSpec, hSpec);

            double measuredW = view.getMeasuredWidth();
            double measuredH = view.getMeasuredHeight();
            if ("Divider".equals(node.componentType()) && measuredH <= 0) {
                measuredH = 1.0;
            } else if ("Progress".equals(node.componentType()) && measuredH <= 0) {
                measuredH = 8.0;
            }
            return new LayoutEngine.MeasuredSize(measuredW, measuredH);
        };
    }

    public AndroidRenderer.AndroidViewHandle mountRoot(
            KairoId rootId,
            String componentType,
            Consumer<UIEvent> eventSink) {
        this.eventSink = eventSink != null ? eventSink : ignored -> {};
        View rootView = viewFactory.createView(rootId, componentType, this.eventSink);
        NodeRecord rec = new NodeRecord(rootId, componentType, rootView);
        nodes.put(rootId, rec);
        rootContainer.removeAllViews();
        rootContainer.addView(rootView);
        nativeEngine.clear();
        nativeEngine.setRoot(rootId.value());
        syncVisuals(rec);
        return new AndroidRenderer.AndroidViewHandle(rootId, componentType, rootView);
    }

    public void applyBatch(List<RenderPatch> patchBatch) {
        if (patchBatch == null || patchBatch.isEmpty()) {
            lastPatchBatchSize = 0;
            return;
        }
        long startNs = System.nanoTime();
        for (RenderPatch patch : patchBatch) {
            if (patch instanceof RenderPatch.CreateNode create) {
                handleCreateNode(create);
            } else if (patch instanceof RenderPatch.UpdateProp prop) {
                handleUpdateProp(prop);
            } else if (patch instanceof RenderPatch.UpdateStyle stylePatch) {
                handleUpdateStyle(stylePatch);
            } else if (patch instanceof RenderPatch.UpdateVisualStates vsPatch) {
                handleUpdateVisualStates(vsPatch);
            } else if (patch instanceof RenderPatch.UpdateBounds boundsPatch) {
                handleUpdateBounds(boundsPatch);
            } else if (patch instanceof RenderPatch.InsertChild insert) {
                handleInsertChild(insert);
            } else if (patch instanceof RenderPatch.MoveChild move) {
                handleMoveChild(move);
            } else if (patch instanceof RenderPatch.RemoveNode remove) {
                handleRemoveNode(remove);
            }
        }
        lastPatchBatchSize = patchBatch.size();
        totalPatchesApplied += patchBatch.size();
        lastFrameDurationMicros = Math.max(1L, (System.nanoTime() - startNs) / 1_000L);
    }

    public void unmountRoot() {
        rootContainer.removeAllViews();
        for (KairoId id : new ArrayList<>(nodes.keySet())) {
            viewFactory.cleanupNode(id);
        }
        nodes.clear();
        pendingBounds.clear();
        nativeEngine.clear();
    }

    public void destroy() {
        unmountRoot();
        nativeEngine.close();
    }

    public Set<KairoId> mountedIds() {
        return Collections.unmodifiableSet(nodes.keySet());
    }

    public RenderEngineStats stats() {
        String backend = nativeEngine.isActive() ? "android-views+cpp-sdf-v1" : "android-views-canvas-v1";
        return new RenderEngineStats(
                backend,
                nativeEngine.isActive(),
                nodes.size(),
                lastPatchBatchSize,
                totalPatchesApplied,
                lastFrameDurationMicros
        );
    }

    public boolean renderNativeSceneToBitmap(Bitmap bitmap) {
        return nativeEngine.renderToBitmap(bitmap);
    }

    public boolean renderNativeSceneToSurface(Surface surface) {
        return nativeEngine.renderToSurface(surface);
    }

    private void handleCreateNode(RenderPatch.CreateNode create) {
        NodeRecord rec = nodes.get(create.id());
        if (rec == null) {
            View view = viewFactory.createView(create.id(), create.componentType(), eventSink);
            rec = new NodeRecord(create.id(), create.componentType(), view);
            nodes.put(create.id(), rec);
        }
        rec.style = create.style() != null ? create.style() : ComputedStyle.empty();
        for (Map.Entry<String, String> entry : create.props().entrySet()) {
            rec.props.put(entry.getKey(), entry.getValue());
            viewFactory.applyProperty(rec.id, rec.view, rec.componentType, entry.getKey(), entry.getValue());
        }
        syncVisuals(rec);
    }

    private void handleUpdateProp(RenderPatch.UpdateProp prop) {
        NodeRecord rec = nodes.get(prop.id());
        if (rec == null) return;
        if (prop.value() == null) {
            rec.props.remove(prop.property());
        } else {
            rec.props.put(prop.property(), prop.value());
        }
        viewFactory.applyProperty(rec.id, rec.view, rec.componentType, prop.property(), prop.value());
        if ("visible".equals(prop.property()) || "overflow".equals(prop.property())) {
            syncVisuals(rec);
        }
    }

    private void handleUpdateStyle(RenderPatch.UpdateStyle stylePatch) {
        NodeRecord rec = nodes.get(stylePatch.id());
        if (rec == null) return;
        rec.style = stylePatch.style() != null ? stylePatch.style() : ComputedStyle.empty();
        syncVisuals(rec);
    }

    private void handleUpdateVisualStates(RenderPatch.UpdateVisualStates vsPatch) {
        NodeRecord rec = nodes.get(vsPatch.id());
        if (rec == null) return;
        rec.visualStates = vsPatch.states() != null ? Set.copyOf(vsPatch.states()) : Set.of();
        syncVisuals(rec);
    }

    private void handleUpdateBounds(RenderPatch.UpdateBounds boundsPatch) {
        NodeRecord rec = nodes.get(boundsPatch.id());
        if (rec == null) return;
        rec.bounds = boundsPatch.bounds() != null ? boundsPatch.bounds() : LayoutBounds.ZERO;
        pendingBounds.put(rec.view, rec.bounds);
        if (rec.view.getParent() instanceof KairoLayoutHostGroup parentGroup) {
            parentGroup.setChildBounds(rec.view, rec.bounds);
        }
        syncNativeOnly(rec);
    }

    private void handleInsertChild(RenderPatch.InsertChild insert) {
        NodeRecord parent = nodes.get(insert.parentId());
        NodeRecord child = nodes.get(insert.childId());
        if (parent == null || child == null) return;

        ViewGroup container = resolveChildHost(parent.view);
        if (container != null) {
            if (child.view.getParent() instanceof ViewGroup oldParent) {
                oldParent.removeView(child.view);
            }
            int idx = Math.max(0, Math.min(insert.index(), container.getChildCount()));
            container.addView(child.view, idx);
            LayoutBounds pending = pendingBounds.get(child.view);
            if (pending != null && container instanceof KairoLayoutHostGroup parentGroup) {
                parentGroup.setChildBounds(child.view, pending);
            }
        }

        parent.childIds.remove(child.id);
        int listIdx = Math.max(0, Math.min(insert.index(), parent.childIds.size()));
        parent.childIds.add(listIdx, child.id);
        syncNativeChildren(parent);
    }

    private void handleMoveChild(RenderPatch.MoveChild move) {
        NodeRecord parent = nodes.get(move.parentId());
        NodeRecord child = nodes.get(move.childId());
        if (parent == null || child == null) return;

        ViewGroup container = resolveChildHost(parent.view);
        if (container != null) {
            container.removeView(child.view);
            int idx = Math.max(0, Math.min(move.toIndex(), container.getChildCount()));
            container.addView(child.view, idx);
            LayoutBounds pending = pendingBounds.get(child.view);
            if (pending != null && container instanceof KairoLayoutHostGroup parentGroup) {
                parentGroup.setChildBounds(child.view, pending);
            }
        }

        parent.childIds.remove(child.id);
        int listIdx = Math.max(0, Math.min(move.toIndex(), parent.childIds.size()));
        parent.childIds.add(listIdx, child.id);
        syncNativeChildren(parent);
    }

    private void handleRemoveNode(RenderPatch.RemoveNode remove) {
        NodeRecord rec = nodes.remove(remove.id());
        viewFactory.cleanupNode(remove.id());
        if (rec != null) {
            pendingBounds.remove(rec.view);
            if (rec.view.getParent() instanceof ViewGroup vg) {
                if (vg instanceof KairoLayoutHostGroup host) {
                    host.clearChildBounds(rec.view);
                }
                vg.removeView(rec.view);
            }
        }
        nativeEngine.removeNode(remove.id().value());
    }

    private ViewGroup resolveChildHost(View parentView) {
        if (parentView instanceof KairoLayoutHostGroup.KairoScrollView scrollView) {
            return scrollView.contentHost();
        }
        if (parentView instanceof ViewGroup vg) {
            return vg;
        }
        return null;
    }

    private void syncVisuals(NodeRecord rec) {
        KairoSceneEncoder.EncodedVisualNode encoded = KairoSceneEncoder.encode(
                rec.componentType,
                rec.style,
                rec.bounds,
                rec.visualStates,
                rec.props
        );
        KairoStyleApplier.apply(rec.view, rec.style, encoded);
        nativeEngine.upsertNode(rec.id.value(), encoded);
    }

    private void syncNativeOnly(NodeRecord rec) {
        if (!nativeEngine.isActive()) return;
        KairoSceneEncoder.EncodedVisualNode encoded = KairoSceneEncoder.encode(
                rec.componentType,
                rec.style,
                rec.bounds,
                rec.visualStates,
                rec.props
        );
        nativeEngine.upsertNode(rec.id.value(), encoded);
    }

    private void syncNativeChildren(NodeRecord parent) {
        if (!nativeEngine.isActive()) return;
        long[] ids = new long[parent.childIds.size()];
        for (int i = 0; i < parent.childIds.size(); i++) {
            ids[i] = parent.childIds.get(i).value();
        }
        nativeEngine.setChildren(parent.id.value(), ids);
    }
}
