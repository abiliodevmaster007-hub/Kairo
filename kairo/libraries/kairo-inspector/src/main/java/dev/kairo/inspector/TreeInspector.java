package dev.kairo.inspector;

import dev.kairo.core.Diagnostic;
import dev.kairo.renderer.api.RenderEngineStats;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.runtime.VirtualNode;
import dev.kairo.state.Derived;
import dev.kairo.state.State;
import dev.kairo.style.StyleResolutionResult;
import dev.kairo.style.StyleTrace;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * DevTools inspector reporting virtual tree, computed styles, cascade traces, reactive bindings,
 * renderer stats, and diagnostics while strictly masking sensitive inputs.
 */
public final class TreeInspector {

    public String dumpTree(KairoRuntime runtime) {
        VirtualNode root = runtime.currentTree();
        if (root == null) return "<unmounted>";
        StringBuilder sb = new StringBuilder();
        dumpNode(runtime, root, 0, sb);
        return sb.toString();
    }

    public String dumpStyleTraceByLogicalId(KairoRuntime runtime, String logicalId) {
        VirtualNode root = runtime.currentTree();
        if (root == null) return "<unmounted>";
        VirtualNode target = findByLogicalId(root, logicalId);
        if (target == null) return "<node not found: " + logicalId + ">";
        StyleResolutionResult result = runtime.styleTrace(target.id());
        if (result == null) return "<no style trace>";
        StringBuilder sb = new StringBuilder();
        sb.append("StyleTrace ").append(target.componentType()).append("#").append(logicalId)
                .append(" theme=").append(runtime.activeTheme()).append('\n');
        for (StyleTrace.Entry entry : result.trace()) {
            sb.append("  [").append(entry.origin()).append("] ")
                    .append(entry.selectorOrLabel())
                    .append(" spec=").append(entry.specificity())
                    .append(" -> ").append(entry.declarations())
                    .append('\n');
        }
        sb.append("  => computed=").append(result.style().asMap());
        return sb.toString();
    }

    public String dumpBindings(KairoRuntime.ScreenModel model) {
        if (model == null) return "<no model>";
        StringBuilder sb = new StringBuilder("Bindings:\n");
        for (Map.Entry<String, Supplier<Object>> entry : model.exposedBindings().entrySet()) {
            Object raw = entry.getValue().get();
            Object unwrapped = unwrapReactive(raw);
            String masked = SecretRedactor.redactBindingValue(entry.getKey(), unwrapped);
            sb.append("  ").append(entry.getKey()).append(" = ").append(masked).append('\n');
        }
        for (Map.Entry<String, State<String>> entry : model.twoWayStringStates().entrySet()) {
            String masked = SecretRedactor.redactBindingValue(entry.getKey(), entry.getValue().get());
            sb.append("  bind:").append(entry.getKey()).append(" = ").append(masked).append('\n');
        }
        return sb.toString();
    }

    public String dumpSnapshot(KairoRuntime runtime, KairoRuntime.ScreenModel model, Renderer renderer) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Kairo Inspector Snapshot ===\n");
        sb.append("theme=").append(runtime.activeTheme())
                .append(" totalPatches=").append(totalPatchCount(runtime));
        if (renderer != null) {
            RenderEngineStats stats = renderer.engineStats();
            sb.append(" backend=").append(stats.backendName())
                    .append(" nativeCpp=").append(stats.nativeAccelerated())
                    .append(" mountedNodes=").append(stats.mountedNodes());
        }
        sb.append('\n');
        sb.append(dumpTree(runtime));
        if (model != null) {
            sb.append(dumpBindings(model));
        }
        return sb.toString();
    }

    public String formatDiagnostics(Diagnostic.Bag diagnostics) {
        if (diagnostics == null || diagnostics.all().isEmpty()) {
            return "Diagnostics: 0 issues";
        }
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics.all()) {
            sb.append(d).append('\n');
        }
        return sb.toString();
    }

    public int totalPatchCount(KairoRuntime runtime) {
        int sum = 0;
        for (List<RenderPatch> batch : runtime.patchHistory()) {
            sum += batch.size();
        }
        return sum;
    }

    private Object unwrapReactive(Object raw) {
        Object cur = raw;
        int guard = 0;
        while (guard++ < 8) {
            if (cur instanceof State<?> s) {
                cur = s.get();
            } else if (cur instanceof Derived<?> d) {
                cur = d.get();
            } else {
                break;
            }
        }
        return cur;
    }

    private VirtualNode findByLogicalId(VirtualNode node, String logicalId) {
        if (logicalId.equals(node.logicalId())) return node;
        for (VirtualNode child : node.children()) {
            VirtualNode found = findByLogicalId(child, logicalId);
            if (found != null) return found;
        }
        return null;
    }

    private void dumpNode(KairoRuntime runtime, VirtualNode node, int depth, StringBuilder sb) {
        var overflow = runtime.overflowInfo(node.id());
        String overflowTag = (overflow != null && overflow.hasOverflow())
                ? " overflow=" + overflow.policy()
                : "";
        sb.append("  ".repeat(depth))
                .append(node.componentType())
                .append(node.logicalId() != null ? "#" + node.logicalId() : "")
                .append(" bounds=").append(node.bounds())
                .append(overflowTag)
                .append(" props=").append(maskProps(node))
                .append('\n');
        for (VirtualNode child : node.children()) {
            dumpNode(runtime, child, depth + 1, sb);
        }
    }

    private String maskProps(VirtualNode node) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : node.props().entrySet()) {
            if (entry.getKey().startsWith("__")) continue;
            if (!first) sb.append(", ");
            first = false;
            String val = SecretRedactor.redactNodeProp(node, entry.getKey(), entry.getValue());
            sb.append(entry.getKey()).append("=").append(val);
        }
        return sb.append("}").toString();
    }
}
