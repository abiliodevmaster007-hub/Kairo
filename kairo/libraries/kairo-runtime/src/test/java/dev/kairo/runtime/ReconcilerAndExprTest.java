package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.state.Derived;
import dev.kairo.state.State;
import dev.kairo.style.ComputedStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class ReconcilerAndExprTest {
    @Test
    void keyedReconcilerEmitsMoveChildAndRemovesStalePropsWithoutRecreatingNodes() {
        KairoId parentId = KairoId.next("list");
        KairoId idA = KairoId.next("rowA");
        KairoId idB = KairoId.next("rowB");
        KairoId idC = KairoId.next("rowC");

        VirtualNode a1 = new VirtualNode(idA, "Text", null, StableKey.of("A"), Map.of("value", "A"), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode b1 = new VirtualNode(idB, "Text", null, StableKey.of("B"), Map.of("value", "B"), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode c1 = new VirtualNode(idC, "Text", null, StableKey.of("C"), Map.of("value", "C"), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode prev = new VirtualNode(parentId, "Column", "list", null, Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), List.of(a1, b1, c1));

        VirtualNode c2 = new VirtualNode(idC, "Text", null, StableKey.of("C"), Map.of("value", "C"), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode a2 = new VirtualNode(idA, "Text", null, StableKey.of("A"), Map.of("value", "A updated"), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode b2 = new VirtualNode(idB, "Text", null, StableKey.of("B"), Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), List.of());
        VirtualNode next = new VirtualNode(parentId, "Column", "list", null, Map.of(), ComputedStyle.empty(), Set.of(), Map.of(), List.of(c2, a2, b2));

        List<RenderPatch> patches = new Reconciler().diff(prev, next);

        assertThat(patches).noneMatch(p -> p instanceof RenderPatch.CreateNode || p instanceof RenderPatch.RemoveNode);
        assertThat(patches).anyMatch(p -> p instanceof RenderPatch.MoveChild mc && mc.childId().equals(idC) && mc.toIndex() == 0);
        assertThat(patches).anyMatch(p -> p instanceof RenderPatch.UpdateProp up && up.id().equals(idA) && "A updated".equals(up.value()));
        assertThat(patches).anyMatch(p -> p instanceof RenderPatch.UpdateProp up && up.id().equals(idB) && "value".equals(up.property()) && up.value() == null);
    }

    @Test
    void expressionEvaluatorResolvesDottedPathsNegationAndDerivedWithoutReflection() {
        State<Boolean> blocked = State.of(false);
        Derived<Boolean> enabled = Derived.derive(() -> !blocked.get());
        ExpressionEvaluator.ExposedObject profile = prop -> switch (prop) {
            case "role" -> "Engenheira";
            case "active" -> enabled;
            default -> null;
        };

        Map<String, Supplier<Object>> scope = Map.of(
                "user", () -> profile,
                "blocked", () -> blocked
        );

        ExpressionEvaluator eval = new ExpressionEvaluator();
        assertThat(eval.evaluate("user.role", scope)).isEqualTo("Engenheira");
        assertThat(eval.evaluate("user.active", scope)).isEqualTo(true);
        assertThat(eval.evaluate("!blocked", scope)).isEqualTo(true);

        blocked.set(true);
        assertThat(eval.evaluate("user.active", scope)).isEqualTo(false);
        enabled.dispose();
    }
}
