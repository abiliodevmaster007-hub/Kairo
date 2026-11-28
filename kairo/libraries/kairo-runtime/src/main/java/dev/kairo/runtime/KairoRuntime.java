package dev.kairo.runtime;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.api.SlotProjection;
import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.css.CssStylesheet;
import dev.kairo.events.CommonEvents;
import dev.kairo.events.EventDispatcher;
import dev.kairo.events.UIEvent;
import dev.kairo.ir.BindingSpec;
import dev.kairo.ir.NodeIR;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.layout.LayoutConstraints;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.lifecycle.Disposable;
import dev.kairo.lifecycle.LifecycleScope;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.state.ReactiveSource;
import dev.kairo.state.State;
import dev.kairo.state.TwoWayLoopGuard;
import dev.kairo.style.ComputedStyle;
import dev.kairo.style.StyleEngine;
import dev.kairo.style.StyleResolutionResult;
import dev.kairo.style.ThemeRegistry;
import dev.kairo.style.ViewportCapabilities;
import dev.kairo.style.VisualState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrates XML IR -> Reactive State/Derived/ObservableList -> Conditional & Keyed Subtrees ->
 * Style Cascade -> Kairo Layout -> Incremental RenderPatches -> EventDispatcher.
 */
public final class KairoRuntime {
    public interface ScreenModel {
        Map<String, Supplier<Object>> exposedBindings();
        Map<String, State<String>> twoWayStringStates();
        Map<String, Runnable> actions();
    }

    private final ComponentRegistry registry;
    private final Renderer renderer;
    private final StyleEngine styleEngine = new StyleEngine();
    private final ThemeRegistry themeRegistry = new ThemeRegistry();
    private final LayoutEngine layoutEngine = new LayoutEngine();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator();
    private final Reconciler reconciler = new Reconciler();
    private final LifecycleScope lifecycleScope = new LifecycleScope();
    private final TwoWayLoopGuard twoWayGuard = new TwoWayLoopGuard();
    private final EventDispatcher eventDispatcher = new EventDispatcher();

    private static final int DEFAULT_IDENTITY_GRACE_FRAMES = 8;

    private static final class IdentityEntry {
        final KairoId id;
        long lastSeenGeneration;
        int inactiveFrames;

        IdentityEntry(KairoId id, long lastSeenGeneration) {
            this.id = id;
            this.lastSeenGeneration = lastSeenGeneration;
        }
    }

    private final Map<String, IdentityEntry> stableNodeIds = new LinkedHashMap<>();
    private final Map<KairoId, LifecycleScope> nodeScopes = new LinkedHashMap<>();
    private final Map<KairoId, VirtualNode> nodeIndex = new LinkedHashMap<>();
    private final Map<KairoId, KairoId> parentByNode = new LinkedHashMap<>();
    private final Map<KairoId, StyleResolutionResult> styleTraces = new LinkedHashMap<>();
    private final Set<KairoId> pressedNodeIds = new HashSet<>();
    private KairoId focusedNodeId;
    private KairoId hoveredNodeId;

    private NodeIR rootIR;
    private CssStylesheet stylesheet;
    private ScreenModel model;
    private LayoutConstraints viewport = LayoutConstraints.fixed(390, 844);
    private ViewportCapabilities viewportCapabilities = ViewportCapabilities.DEFAULT;
    private VirtualNode currentTree;
    private KairoId mountedRootId;
    private LayoutEngine.LayoutResult lastLayoutResult;
    private RuntimeMetrics lastMetrics = RuntimeMetrics.ZERO;
    private final List<List<RenderPatch>> patchHistory = new ArrayList<>();
    private final List<RuntimeMetrics> metricsHistory = new ArrayList<>();
    private long renderGeneration = 0L;
    private int identityGraceFrames = DEFAULT_IDENTITY_GRACE_FRAMES;
    private long activeStyleNanos;
    private long activeLayoutNanos;
    private long activeTreeBuildNanos;
    private int activeNodesRebuilt;
    private int activeLayoutNodesProcessed;
    private boolean isRecomputing;
    private boolean recomputeQueued;

    public KairoRuntime(ComponentRegistry registry, Renderer renderer) {
        this.registry = registry;
        this.renderer = renderer;
    }

    public LifecycleScope lifecycleScope() {
        return lifecycleScope;
    }

    public void setIdentityGraceFrames(int graceFrames) {
        this.identityGraceFrames = Math.max(0, graceFrames);
    }

    public int identityGraceFrames() {
        return identityGraceFrames;
    }

    public int retainedIdentityCount() {
        return stableNodeIds.size();
    }

    public int listenerCount(KairoId nodeId) {
        return eventDispatcher.listenerCount(nodeId);
    }

    public int ownedNodeScopeCount() {
        return nodeScopes.size();
    }

    public int retainedListenerDisposableCount() {
        int total = 0;
        for (LifecycleScope scope : nodeScopes.values()) {
            total += scope.activeMountDisposableCount();
        }
        return total;
    }

    public void mount(NodeIR rootIR, CssStylesheet stylesheet, ScreenModel model, LayoutConstraints viewport) {
        if (lifecycleScope.isMounted() || currentTree != null || mountedRootId != null) {
            unmount();
        }
        this.rootIR = rootIR;
        this.stylesheet = stylesheet;
        this.model = model;
        this.viewport = viewport;

        lifecycleScope.onMount();
        subscribeModelDependencies();

        long frameStart = System.nanoTime();
        VirtualNode initial = buildAndLayoutTree();
        List<RenderPatch> patches = List.of();
        long reconcileNanos = 0L;
        long patchNanos = 0L;
        if (initial != null) {
            long recStart = System.nanoTime();
            patches = reconciler.diffMountedRoot(initial);
            reconcileNanos = System.nanoTime() - recStart;
            long applyStart = System.nanoTime();
            mountNewRoot(initial, patches);
            patchNanos = System.nanoTime() - applyStart;
        } else {
            mountedRootId = null;
        }
        long totalNanos = System.nanoTime() - frameStart;
        recordMetrics(reconcileNanos, patchNanos, totalNanos, patches);
        this.currentTree = initial;
    }

    public void batch(Runnable mutations) {
        State.transaction(mutations);
    }

    public void setTheme(String themeName) {
        themeRegistry.setActiveTheme(themeName);
        recompute();
    }

    public String activeTheme() {
        return themeRegistry.activeTheme();
    }

    public void updateTheme(Map<String, String> tokens) {
        themeRegistry.putTokens(tokens);
        recompute();
    }

    public void setViewportCapabilities(ViewportCapabilities capabilities) {
        this.viewportCapabilities = capabilities != null ? capabilities : ViewportCapabilities.DEFAULT;
        recompute();
    }

    public StyleResolutionResult styleTrace(KairoId id) {
        return styleTraces.get(id);
    }

    public <E extends UIEvent> Disposable addEventListener(KairoId nodeId, Class<E> type, Consumer<E> handler) {
        if (lifecycleScope.isDestroyed()) {
            return () -> {};
        }
        return registerNodeDisposable(nodeId, eventDispatcher.addListener(nodeId, type, handler));
    }

    public <E extends UIEvent> Disposable addCaptureEventListener(KairoId nodeId, Class<E> type, Consumer<E> handler) {
        if (lifecycleScope.isDestroyed()) {
            return () -> {};
        }
        return registerNodeDisposable(nodeId, eventDispatcher.addCaptureListener(nodeId, type, handler));
    }

    public void recompute() {
        if (rootIR == null || !lifecycleScope.isMounted()) return;
        if (isRecomputing) {
            recomputeQueued = true;
            return;
        }
        isRecomputing = true;
        try {
            do {
                recomputeQueued = false;
                long frameStart = System.nanoTime();
                VirtualNode prev = this.currentTree;
                VirtualNode next = buildAndLayoutTree();
                List<RenderPatch> patches = List.of();
                long reconcileNanos = 0L;
                long patchNanos = 0L;

                if (prev == null && next != null) {
                    long recStart = System.nanoTime();
                    patches = reconciler.diffMountedRoot(next);
                    reconcileNanos = System.nanoTime() - recStart;
                    long applyStart = System.nanoTime();
                    mountNewRoot(next, patches);
                    patchNanos = System.nanoTime() - applyStart;
                } else if (prev != null && next == null) {
                    long applyStart = System.nanoTime();
                    unmountCurrentRoot(prev.id());
                    patchNanos = System.nanoTime() - applyStart;
                } else if (prev != null && next != null
                        && (!Objects.equals(prev.id(), next.id()) || !prev.componentType().equals(next.componentType()))) {
                    long recStart = System.nanoTime();
                    patches = reconciler.diffMountedRoot(next);
                    reconcileNanos = System.nanoTime() - recStart;
                    long applyStart = System.nanoTime();
                    replaceRoot(prev, next, patches);
                    patchNanos = System.nanoTime() - applyStart;
                } else if (prev != null && next != null) {
                    long recStart = System.nanoTime();
                    patches = reconciler.diff(prev, next);
                    reconcileNanos = System.nanoTime() - recStart;
                    if (!patches.isEmpty()) {
                        long applyStart = System.nanoTime();
                        applyPatches(patches);
                        patchNanos = System.nanoTime() - applyStart;
                    }
                }

                long totalNanos = System.nanoTime() - frameStart;
                recordMetrics(reconcileNanos, patchNanos, totalNanos, patches);
                this.currentTree = next;
            } while (recomputeQueued);
        } finally {
            isRecomputing = false;
        }
    }

    public void unmount() {
        if (mountedRootId != null) {
            renderer.unmountRoot(mountedRootId);
            mountedRootId = null;
        } else if (currentTree != null) {
            renderer.unmountRoot(currentTree.id());
        }
        for (KairoId id : new ArrayList<>(nodeIndex.keySet())) {
            eventDispatcher.clearListeners(id);
        }
        for (KairoId id : new ArrayList<>(nodeScopes.keySet())) {
            eventDispatcher.clearListeners(id);
            disposeNodeScope(id);
        }
        nodeScopes.clear();
        nodeIndex.clear();
        parentByNode.clear();
        styleTraces.clear();
        pressedNodeIds.clear();
        focusedNodeId = null;
        hoveredNodeId = null;
        lifecycleScope.onUnmount();
        currentTree = null;
        lastLayoutResult = null;
        lastMetrics = RuntimeMetrics.ZERO;
    }

    private void mountNewRoot(VirtualNode root, List<RenderPatch> patches) {
        renderer.mountRoot(root.id(), root.componentType(), this::dispatchEvent);
        mountedRootId = root.id();
        try {
            applyPatches(patches);
        } catch (RuntimeException ex) {
            try {
                renderer.unmountRoot(root.id());
            } catch (RuntimeException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            mountedRootId = null;
            this.currentTree = null;
            throw ex;
        }
    }

    private void unmountCurrentRoot(KairoId prevRootId) {
        clearNodeInteractionAndScope(prevRootId);
        if (mountedRootId != null) {
            renderer.unmountRoot(mountedRootId);
            mountedRootId = null;
        } else if (prevRootId != null) {
            renderer.unmountRoot(prevRootId);
        }
    }

    private void replaceRoot(VirtualNode prev, VirtualNode next, List<RenderPatch> patches) {
        clearNodeInteractionAndScope(prev.id());
        if (mountedRootId != null) {
            renderer.unmountRoot(mountedRootId);
            mountedRootId = null;
        }
        try {
            mountNewRoot(next, patches);
        } catch (RuntimeException ex) {
            this.currentTree = null;
            throw ex;
        }
    }

    private void clearNodeInteractionAndScope(KairoId nodeId) {
        if (nodeId == null) return;
        pressedNodeIds.remove(nodeId);
        if (Objects.equals(focusedNodeId, nodeId)) {
            focusedNodeId = null;
        }
        if (Objects.equals(hoveredNodeId, nodeId)) {
            hoveredNodeId = null;
        }
        disposeNodeScope(nodeId);
        eventDispatcher.clearListeners(nodeId);
    }

    private Disposable registerNodeDisposable(KairoId nodeId, Disposable raw) {
        if (raw == null) {
            return () -> {};
        }
        LifecycleScope nodeScope = nodeScopes.computeIfAbsent(nodeId, ignored -> lifecycleScope.createChildScope());
        AtomicBoolean disposed = new AtomicBoolean(false);
        final Disposable[] holder = new Disposable[1];
        Disposable managed = () -> {
            if (disposed.compareAndSet(false, true)) {
                try {
                    raw.dispose();
                } finally {
                    nodeScope.releaseMountDisposable(holder[0]);
                    if (nodeScope.activeMountDisposableCount() == 0
                            && !nodeScope.isDestroyed()
                            && nodeScopes.get(nodeId) == nodeScope) {
                        disposeNodeScope(nodeId);
                    }
                }
            }
        };
        holder[0] = managed;
        return nodeScope.ownForMount(managed);
    }

    private void disposeNodeScope(KairoId nodeId) {
        LifecycleScope scope = nodeScopes.remove(nodeId);
        if (scope != null && !scope.isDestroyed()) {
            scope.onDestroy();
        }
    }

    public void destroy() {
        unmount();
        stableNodeIds.clear();
        lifecycleScope.onDestroy();
    }

    public VirtualNode currentTree() {
        return currentTree;
    }

    public LayoutEngine.LayoutResult lastLayoutResult() {
        return lastLayoutResult;
    }

    public RuntimeMetrics lastMetrics() {
        return lastMetrics;
    }

    public List<RuntimeMetrics> metricsHistory() {
        return List.copyOf(metricsHistory);
    }

    public dev.kairo.layout.OverflowPolicy.OverflowInfo overflowInfo(KairoId nodeId) {
        return lastLayoutResult != null ? lastLayoutResult.overflowDetails().get(nodeId) : null;
    }

    public List<List<RenderPatch>> patchHistory() {
        return List.copyOf(patchHistory);
    }

    public void dispatchEvent(UIEvent event) {
        if (event == null || !lifecycleScope.isMounted()) return;
        VirtualNode target = nodeIndex.get(event.target());
        if (target == null) return;

        List<KairoId> path = buildPathFromRoot(target.id());
        eventDispatcher.dispatch(event, path);
        if (event.isDefaultPrevented()) {
            return;
        }

        if (event instanceof CommonEvents.InputEvent inputEvent) {
            String bindTarget = findTwoWayExpression(target);
            if (bindTarget != null && model != null) {
                State<String> state = model.twoWayStringStates().get(bindTarget);
                if (state != null) {
                    twoWayGuard.runFromView(() -> State.transaction(() -> state.set(inputEvent.value())));
                }
            }
        } else if (event instanceof CommonEvents.ClickEvent) {
            invokeAction(target.eventHandlers().get("onClick"));
        } else if (event instanceof CommonEvents.SubmitEvent) {
            invokeAction(target.eventHandlers().get("onSubmit"));
        } else if (event instanceof CommonEvents.FocusEvent focusEvent) {
            KairoId nextFocused = focusEvent.focused() ? target.id() : null;
            if (!Objects.equals(focusedNodeId, nextFocused)) {
                focusedNodeId = nextFocused;
                recompute();
            }
        } else if (event instanceof CommonEvents.PointerPressEvent pressEvent) {
            boolean changed = pressEvent.pressed()
                    ? pressedNodeIds.add(target.id())
                    : pressedNodeIds.remove(target.id());
            if (changed) {
                recompute();
            }
        } else if (event instanceof CommonEvents.HoverEvent hoverEvent) {
            KairoId nextHovered = hoverEvent.hovered() ? target.id() : null;
            if (!Objects.equals(hoveredNodeId, nextHovered)) {
                hoveredNodeId = nextHovered;
                recompute();
            }
        }
    }

    private void invokeAction(String handlerName) {
        if (handlerName == null || model == null) return;
        Runnable action = model.actions().get(handlerName);
        if (action != null) {
            State.transaction(action);
        }
    }

    private List<KairoId> buildPathFromRoot(KairoId targetId) {
        List<KairoId> path = new ArrayList<>();
        KairoId cursor = targetId;
        while (cursor != null) {
            path.add(cursor);
            cursor = parentByNode.get(cursor);
        }
        Collections.reverse(path);
        return path;
    }

    private String findTwoWayExpression(VirtualNode node) {
        return node.props().get("__bind:value");
    }

    private void subscribeModelDependencies() {
        if (model == null) return;
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Supplier<Object> supplier : model.exposedBindings().values()) {
            Object obj = supplier.get();
            if (!seen.add(obj)) continue;
            if (obj instanceof ReactiveSource reactive) {
                lifecycleScope.ownForMount(reactive.subscribeAny(this::recompute));
            }
        }
        for (State<String> twoWay : model.twoWayStringStates().values()) {
            if (seen.add(twoWay)) {
                lifecycleScope.ownForMount(twoWay.subscribeAny(this::recompute));
            }
        }
    }

    private VirtualNode buildAndLayoutTree() {
        Set<KairoId> previousActiveIds = new HashSet<>(nodeIndex.keySet());
        nodeIndex.clear();
        parentByNode.clear();
        styleTraces.clear();
        renderGeneration++;
        activeStyleNanos = 0L;
        activeLayoutNanos = 0L;
        activeTreeBuildNanos = 0L;
        activeNodesRebuilt = 0;
        activeLayoutNodesProcessed = 0;

        long buildStart = System.nanoTime();
        VirtualNode built = buildNode(rootIR, null, "root", ComputedStyle.empty(), List.of());
        long buildEnd = System.nanoTime();
        activeTreeBuildNanos = Math.max(0L, (buildEnd - buildStart) - activeStyleNanos);
        activeNodesRebuilt = nodeIndex.size();
        evictExpiredIdentitiesAndCleanState(previousActiveIds);
        if (built == null) {
            return null;
        }

        long layoutStart = System.nanoTime();
        LayoutEngine.LayoutNode layoutRoot = toLayoutNode(built);
        LayoutEngine.LayoutResult layoutResult = layoutEngine.compute(layoutRoot, viewport, renderer.leafMeasurer());
        this.lastLayoutResult = layoutResult;
        applyBounds(built, layoutResult.boundsByNode());
        activeLayoutNanos = Math.max(0L, System.nanoTime() - layoutStart);
        activeLayoutNodesProcessed = layoutResult.boundsByNode().size();
        return built;
    }

    private void evictExpiredIdentitiesAndCleanState(Set<KairoId> previousActiveIds) {
        for (KairoId prevId : previousActiveIds) {
            if (!nodeIndex.containsKey(prevId)) {
                pressedNodeIds.remove(prevId);
                if (Objects.equals(focusedNodeId, prevId)) {
                    focusedNodeId = null;
                }
                if (Objects.equals(hoveredNodeId, prevId)) {
                    hoveredNodeId = null;
                }
            }
        }
        var it = stableNodeIds.entrySet().iterator();
        while (it.hasNext()) {
            IdentityEntry entry = it.next().getValue();
            if (entry.lastSeenGeneration != renderGeneration) {
                entry.inactiveFrames++;
                if (entry.inactiveFrames > identityGraceFrames) {
                    disposeNodeScope(entry.id);
                    eventDispatcher.clearListeners(entry.id);
                    it.remove();
                }
            }
        }
    }

    private void recordMetrics(long reconcileNanos, long patchNanos, long totalNanos, List<RenderPatch> patches) {
        RuntimeMetrics metrics = RuntimeMetrics.fromFrame(
                activeTreeBuildNanos,
                activeStyleNanos,
                activeLayoutNanos,
                reconcileNanos,
                patchNanos,
                totalNanos,
                activeNodesRebuilt,
                activeLayoutNodesProcessed,
                patches
        );
        this.lastMetrics = metrics;
        this.metricsHistory.add(metrics);
    }

    private VirtualNode buildNode(
            NodeIR ir,
            KairoId parentId,
            String pathKey,
            ComputedStyle parentStyle,
            List<StyleEngine.StyleContext> ancestors) {
        if (!evaluateConditionDirective(ir)) {
            return null;
        }
        ir = expandIfCustom(ir, 0);
        if (ir == null || !evaluateConditionDirective(ir)) {
            return null;
        }

        StableKey stableKey = resolveStableKey(ir);
        String idKey = stableKey != null
                ? "key:" + ir.componentType() + ":" + stableKey.value()
                : (ir.logicalId() != null
                    ? "id:" + ir.componentType() + ":" + ir.logicalId()
                    : "path:" + ir.componentType() + ":" + pathKey);
        IdentityEntry idEntry = stableNodeIds.get(idKey);
        if (idEntry == null) {
            idEntry = new IdentityEntry(KairoId.next(ir.logicalId()), renderGeneration);
            stableNodeIds.put(idKey, idEntry);
        } else {
            idEntry.lastSeenGeneration = renderGeneration;
            idEntry.inactiveFrames = 0;
        }
        KairoId id = idEntry.id;

        Map<String, String> props = new LinkedHashMap<>();
        registry.find(ir.componentType()).ifPresent(desc -> props.putAll(desc.defaultProperties()));
        props.putAll(ir.properties());
        props.remove("if");
        props.remove("when");

        Map<String, Supplier<Object>> scope = model != null ? model.exposedBindings() : Map.of();
        for (BindingSpec binding : ir.bindings().values()) {
            if ("if".equals(binding.targetProperty()) || "when".equals(binding.targetProperty())) {
                continue;
            }
            Object evaluated = evaluator.evaluate(binding.expression(), scope);
            props.put(binding.targetProperty(), String.valueOf(evaluated));
            if (binding.twoWay()) {
                props.put("__bind:" + binding.targetProperty(), binding.expression());
            }
        }

        Set<VisualState> states = EnumSet.noneOf(VisualState.class);
        if ("true".equalsIgnoreCase(props.get("disabled"))) states.add(VisualState.DISABLED);
        if ("true".equalsIgnoreCase(props.get("error"))) states.add(VisualState.ERROR);
        if ("true".equalsIgnoreCase(props.get("checked"))) states.add(VisualState.CHECKED);
        if ("true".equalsIgnoreCase(props.get("selected"))) states.add(VisualState.SELECTED);
        if (Objects.equals(focusedNodeId, id)) states.add(VisualState.FOCUSED);
        if (pressedNodeIds.contains(id)) states.add(VisualState.PRESSED);
        if (Objects.equals(hoveredNodeId, id)) states.add(VisualState.HOVER);

        long styleStart = System.nanoTime();
        StyleEngine.StyleContext styleCtx = StyleEngine.StyleContext.from(ir, states, ancestors);
        StyleResolutionResult styleResult = styleEngine.resolveWithTrace(
                stylesheet,
                styleCtx,
                parentStyle,
                themeRegistry,
                themeRegistry.activeTheme(),
                Map.of(),
                props.get("style"),
                viewportCapabilities
        );
        activeStyleNanos += Math.max(0L, System.nanoTime() - styleStart);
        ComputedStyle style = styleResult.style();
        styleTraces.put(id, styleResult);

        List<StyleEngine.StyleContext> childAncestors = new ArrayList<>(ancestors);
        childAncestors.add(styleCtx);

        List<VirtualNode> children = new ArrayList<>();
        for (int i = 0; i < ir.children().size(); i++) {
            NodeIR childIR = ir.children().get(i);
            String childKeyHint = childIR.stableKeyExpr() != null ? childIR.stableKeyExpr() : String.valueOf(i);
            VirtualNode childNode = buildNode(
                    childIR,
                    id,
                    pathKey + "/" + childKeyHint + ":" + childIR.componentType(),
                    style,
                    childAncestors
            );
            if (childNode != null) {
                children.add(childNode);
            }
        }

        VirtualNode node = new VirtualNode(id, ir.componentType(), ir.logicalId(), stableKey, props, style, states, ir.eventHandlers(), children);
        nodeIndex.put(id, node);
        if (parentId != null) {
            parentByNode.put(id, parentId);
        }
        return node;
    }

    private NodeIR expandIfCustom(NodeIR ir, int depth) {
        if (depth > 16) {
            throw new IllegalStateException("Custom component expansion exceeded max depth (16) for <" + ir.componentType() + ">");
        }
        var expanderOpt = registry.findExpander(ir.componentType());
        if (expanderOpt.isEmpty()) {
            return ir;
        }
        Map<String, String> mergedProps = new LinkedHashMap<>();
        registry.find(ir.componentType()).ifPresent(desc -> mergedProps.putAll(desc.defaultProperties()));
        mergedProps.putAll(ir.properties());
        Map<String, Supplier<Object>> scope = model != null ? model.exposedBindings() : Map.of();
        for (BindingSpec binding : ir.bindings().values()) {
            if (!"if".equals(binding.targetProperty()) && !"when".equals(binding.targetProperty())) {
                mergedProps.put(binding.targetProperty(), String.valueOf(evaluator.evaluate(binding.expression(), scope)));
            }
        }
        NodeIR normalized = new NodeIR(
                ir.componentType(),
                ir.logicalId(),
                ir.stableKeyExpr(),
                ir.styleClasses(),
                mergedProps,
                ir.bindings(),
                ir.eventHandlers(),
                ir.slotName(),
                ir.children(),
                ir.sourceLocation()
        );
        Map<String, NodeIR> slots = SlotProjection.extractSlots(normalized);
        NodeIR expanded = expanderOpt.get().expand(normalized, slots);
        NodeIR projected = SlotProjection.projectSlots(expanded, slots);
        if (projected == null) return null;

        List<String> combinedClasses = new ArrayList<>(projected.styleClasses());
        for (String cls : ir.styleClasses()) {
            if (!combinedClasses.contains(cls)) {
                combinedClasses.add(cls);
            }
        }
        String effectiveId = ir.logicalId() != null ? ir.logicalId() : projected.logicalId();
        NodeIR wrapped = new NodeIR(
                projected.componentType(),
                effectiveId,
                ir.stableKeyExpr() != null ? ir.stableKeyExpr() : projected.stableKeyExpr(),
                combinedClasses,
                projected.properties(),
                projected.bindings(),
                projected.eventHandlers(),
                ir.slotName(),
                projected.children(),
                projected.sourceLocation()
        );
        return expandIfCustom(wrapped, depth + 1);
    }

    private boolean evaluateConditionDirective(NodeIR ir) {
        BindingSpec condBinding = ir.bindings().get("if");
        if (condBinding == null) condBinding = ir.bindings().get("when");
        Map<String, Supplier<Object>> scope = model != null ? model.exposedBindings() : Map.of();
        if (condBinding != null) {
            return ExpressionEvaluator.asBoolean(evaluator.evaluate(condBinding.expression(), scope));
        }
        String staticCond = ir.properties().get("if");
        if (staticCond == null) staticCond = ir.properties().get("when");
        if (staticCond != null) {
            return !"false".equalsIgnoreCase(staticCond.trim());
        }
        return true;
    }

    private StableKey resolveStableKey(NodeIR ir) {
        if (ir.stableKeyExpr() == null || ir.stableKeyExpr().isBlank()) return null;
        String expr = ir.stableKeyExpr().trim();
        if (expr.startsWith("{{") && expr.endsWith("}}")) {
            String inner = expr.substring(2, expr.length() - 2).trim();
            Object resolved = evaluator.evaluate(inner, model != null ? model.exposedBindings() : Map.of());
            return StableKey.of(String.valueOf(resolved));
        }
        return StableKey.of(expr);
    }

    private LayoutEngine.LayoutNode toLayoutNode(VirtualNode node) {
        LayoutEngine.Axis axis = registry.find(node.componentType())
                .map(ComponentDescriptor::layoutAxis)
                .map(hint -> switch (hint) {
                    case HORIZONTAL -> LayoutEngine.Axis.HORIZONTAL;
                    case OVERLAY -> LayoutEngine.Axis.OVERLAY;
                    default -> LayoutEngine.Axis.VERTICAL;
                })
                .orElse(LayoutEngine.Axis.VERTICAL);

        List<LayoutEngine.LayoutNode> children = node.children().stream().map(this::toLayoutNode).toList();
        return new LayoutEngine.LayoutNode(node.id(), node.componentType(), axis, node.style(), node.props(), children);
    }

    private void applyBounds(VirtualNode node, Map<KairoId, LayoutBounds> boundsByNode) {
        node.setBounds(boundsByNode.getOrDefault(node.id(), LayoutBounds.ZERO));
        for (VirtualNode child : node.children()) {
            applyBounds(child, boundsByNode);
        }
    }

    private void applyPatches(List<RenderPatch> patches) {
        patchHistory.add(patches);
        renderer.apply(patches);
    }
}
