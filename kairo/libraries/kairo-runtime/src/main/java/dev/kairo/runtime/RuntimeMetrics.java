package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.renderer.api.RenderPatch;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-frame runtime timing and reconciliation cardinality metrics for KairoRuntime
 * and Kairo Inspector diagnostics.
 */
public record RuntimeMetrics(
        long treeConstructionNanos,
        long styleResolutionNanos,
        long layoutNanos,
        long reconciliationNanos,
        long patchApplicationNanos,
        long totalNanos,
        int nodesRebuilt,
        int layoutNodesProcessed,
        int nodesCreated,
        int nodesChanged,
        int nodesMoved,
        int nodesRemoved,
        int patchesEmitted
) {
    public static final RuntimeMetrics ZERO = new RuntimeMetrics(
            0L, 0L, 0L, 0L, 0L, 0L, 0, 0, 0, 0, 0, 0, 0
    );

    public static RuntimeMetrics fromFrame(
            long treeConstructionNanos,
            long styleResolutionNanos,
            long layoutNanos,
            long reconciliationNanos,
            long patchApplicationNanos,
            long totalNanos,
            int nodesRebuilt,
            int layoutNodesProcessed,
            List<RenderPatch> patches) {
        Set<KairoId> createdIds = new HashSet<>();
        Set<KairoId> changedIds = new HashSet<>();
        int moved = 0;
        int removed = 0;

        if (patches != null) {
            for (RenderPatch patch : patches) {
                if (patch instanceof RenderPatch.CreateNode cn) {
                    createdIds.add(cn.id());
                } else if (patch instanceof RenderPatch.RemoveNode rn) {
                    removed++;
                } else if (patch instanceof RenderPatch.MoveChild mc) {
                    moved++;
                    if (!createdIds.contains(mc.childId())) {
                        changedIds.add(mc.childId());
                    }
                } else if (patch instanceof RenderPatch.UpdateProp up) {
                    if (!createdIds.contains(up.id())) {
                        changedIds.add(up.id());
                    }
                } else if (patch instanceof RenderPatch.UpdateStyle us) {
                    if (!createdIds.contains(us.id())) {
                        changedIds.add(us.id());
                    }
                } else if (patch instanceof RenderPatch.UpdateVisualStates uv) {
                    if (!createdIds.contains(uv.id())) {
                        changedIds.add(uv.id());
                    }
                } else if (patch instanceof RenderPatch.UpdateBounds ub) {
                    if (!createdIds.contains(ub.id())) {
                        changedIds.add(ub.id());
                    }
                }
            }
        }

        return new RuntimeMetrics(
                Math.max(0L, treeConstructionNanos),
                Math.max(0L, styleResolutionNanos),
                Math.max(0L, layoutNanos),
                Math.max(0L, reconciliationNanos),
                Math.max(0L, patchApplicationNanos),
                Math.max(0L, totalNanos),
                Math.max(0, nodesRebuilt),
                Math.max(0, layoutNodesProcessed),
                createdIds.size(),
                changedIds.size(),
                moved,
                removed,
                patches != null ? patches.size() : 0
        );
    }

    public double treeConstructionMillis() {
        return treeConstructionNanos / 1_000_000.0;
    }

    public double styleResolutionMillis() {
        return styleResolutionNanos / 1_000_000.0;
    }

    public double layoutMillis() {
        return layoutNanos / 1_000_000.0;
    }

    public double reconciliationMillis() {
        return reconciliationNanos / 1_000_000.0;
    }

    public double patchApplicationMillis() {
        return patchApplicationNanos / 1_000_000.0;
    }

    public double totalMillis() {
        return totalNanos / 1_000_000.0;
    }
}
