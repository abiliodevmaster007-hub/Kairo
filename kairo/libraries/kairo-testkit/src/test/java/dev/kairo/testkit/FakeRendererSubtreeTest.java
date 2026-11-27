package dev.kairo.testkit;

import dev.kairo.core.KairoId;
import dev.kairo.layout.LayoutBounds;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.style.ComputedStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class FakeRendererSubtreeTest {

    @Test
    void removeNodeRecursivelyEliminatesDescendantsAndDetachesFromParent() {
        FakeRenderer renderer = new FakeRenderer();
        KairoId root = KairoId.next("root");
        KairoId branchA = KairoId.next("branchA");
        KairoId leafA1 = KairoId.next("leafA1");
        KairoId leafA2 = KairoId.next("leafA2");
        KairoId branchB = KairoId.next("branchB");

        renderer.mountRoot(root, "Screen", e -> {});
        renderer.apply(List.of(
                new RenderPatch.CreateNode(root, "Screen", Map.of(), ComputedStyle.empty()),
                new RenderPatch.CreateNode(branchA, "Column", Map.of(), ComputedStyle.empty()),
                new RenderPatch.CreateNode(leafA1, "Text", Map.of("value", "1"), ComputedStyle.empty()),
                new RenderPatch.CreateNode(leafA2, "Text", Map.of("value", "2"), ComputedStyle.empty()),
                new RenderPatch.CreateNode(branchB, "Text", Map.of("value", "B"), ComputedStyle.empty()),
                new RenderPatch.InsertChild(branchA, leafA1, 0),
                new RenderPatch.InsertChild(branchA, leafA2, 1),
                new RenderPatch.InsertChild(root, branchA, 0),
                new RenderPatch.InsertChild(root, branchB, 1),
                new RenderPatch.UpdateBounds(root, new LayoutBounds(0, 0, 300, 400))
        ));

        assertThat(renderer.mountedIds()).containsExactlyInAnyOrder(root, branchA, leafA1, leafA2, branchB);

        // Removing branchA must recursively purge branchA, leafA1, and leafA2 with zero orphans
        renderer.apply(List.of(new RenderPatch.RemoveNode(branchA)));

        assertThat(renderer.mountedIds()).containsExactlyInAnyOrder(root, branchB);
        assertThat(renderer.findById(branchA)).isNull();
        assertThat(renderer.findById(leafA1)).isNull();
        assertThat(renderer.findById(leafA2)).isNull();
        assertThat(renderer.findById(root).childOrder).containsExactly(branchB);
    }
}
