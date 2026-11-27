package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.style.ComputedStyle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class ReconcilerScaleBenchmarkTest {

    @Test
    void keyedReconciliationScalesLinearlyAcrossTenToTenThousandChildren() {
        Reconciler reconciler = new Reconciler();
        int[] scales = { 10, 100, 1_000, 10_000 };

        for (int size : scales) {
            List<VirtualNode> prevChildren = new ArrayList<>(size);
            List<VirtualNode> nextRotated = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                StableKey key = StableKey.of("row-" + i);
                KairoId id = KairoId.next("r" + i);
                prevChildren.add(rowNode(id, key, "Item " + i));
            }
            // Rotate tail element to head (triggers 1 MoveChild + O(n) aligned fast-path)
            nextRotated.add(prevChildren.get(size - 1));
            for (int i = 0; i < size - 1; i++) {
                nextRotated.add(prevChildren.get(i));
            }

            VirtualNode prevRoot = new VirtualNode(KairoId.next("list"), "Column", "list", null,
                    Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), prevChildren);
            VirtualNode nextRoot = new VirtualNode(prevRoot.id(), "Column", "list", null,
                    Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), nextRotated);

            long startNs = System.nanoTime();
            List<RenderPatch> patches = reconciler.diff(prevRoot, nextRoot);
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

            assertThat(patches).hasSize(1);
            assertThat(patches.get(0)).isInstanceOf(RenderPatch.MoveChild.class);
            assertThat(elapsedMs).isLessThan(2_000L);
        }
    }

    @Test
    void handlesBulkInsertionsRemovalsAndMiddleShufflesAtOneThousandNodes() {
        Reconciler reconciler = new Reconciler();
        int size = 1_000;
        List<VirtualNode> prevChildren = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            prevChildren.add(rowNode(KairoId.next("n" + i), StableKey.of("k-" + i), "v" + i));
        }

        // Remove all even indices, keep odd indices reversed, append 200 new nodes
        List<VirtualNode> nextChildren = new ArrayList<>();
        for (int i = size - 1; i >= 0; i -= 2) {
            VirtualNode existing = prevChildren.get(i);
            nextChildren.add(rowNode(existing.id(), existing.stableKey(), "updated-" + i));
        }
        for (int i = 0; i < 200; i++) {
            nextChildren.add(rowNode(KairoId.next("new" + i), StableKey.of("new-" + i), "fresh-" + i));
        }

        VirtualNode prevRoot = new VirtualNode(KairoId.next("col"), "Column", "col", null,
                Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), prevChildren);
        VirtualNode nextRoot = new VirtualNode(prevRoot.id(), "Column", "col", null,
                Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), nextChildren);

        long startNs = System.nanoTime();
        List<RenderPatch> patches = reconciler.diff(prevRoot, nextRoot);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        long removedCount = patches.stream().filter(RenderPatch.RemoveNode.class::isInstance).count();
        long createdCount = patches.stream().filter(RenderPatch.CreateNode.class::isInstance).count();
        assertThat(removedCount).isEqualTo(500);
        assertThat(createdCount).isEqualTo(200);
        assertThat(elapsedMs).isLessThan(2_000L);
    }

    private static VirtualNode rowNode(KairoId id, StableKey key, String text) {
        return new VirtualNode(id, "Text", null, key,
                Map.of("value", text), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
    }
}
