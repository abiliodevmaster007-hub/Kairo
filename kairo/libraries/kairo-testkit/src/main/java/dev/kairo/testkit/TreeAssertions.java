package dev.kairo.testkit;

import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.runtime.VirtualNode;

import java.util.List;

/**
 * Virtual UI tree and incremental patch assertions for JVM tests.
 */
public final class TreeAssertions {
    private TreeAssertions() {}

    public static VirtualNode findByLogicalId(VirtualNode root, String logicalId) {
        if (root == null) return null;
        if (logicalId.equals(root.logicalId())) return root;
        for (VirtualNode child : root.children()) {
            VirtualNode found = findByLogicalId(child, logicalId);
            if (found != null) return found;
        }
        return null;
    }

    public static void assertNoNodeCreationsInLastBatch(KairoRuntime runtime) {
        List<List<RenderPatch>> history = runtime.patchHistory();
        if (history.isEmpty()) {
            throw new AssertionError("Expected at least one patch batch");
        }
        List<RenderPatch> last = history.get(history.size() - 1);
        boolean created = last.stream().anyMatch(p -> p instanceof RenderPatch.CreateNode);
        if (created) {
            throw new AssertionError("Expected incremental update without CreateNode, but found CreateNode in " + last);
        }
    }
}
