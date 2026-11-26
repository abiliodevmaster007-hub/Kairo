package dev.kairo.components.list;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentDescriptor.ChildrenPolicy;
import dev.kairo.components.api.ComponentDescriptor.LayoutAxisHint;
import dev.kairo.components.api.ComponentDescriptor.SemanticRole;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.core.StableKey;
import dev.kairo.events.CommonEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Key-based incremental list diffing & virtualization contract (supports 10,000+ items without full rebuild).
 */
public final class KairoListDiff {
    public sealed interface Op<T> {
        record Insert<T>(int index, StableKey key, T item) implements Op<T> {}
        record Remove<T>(int index, StableKey key) implements Op<T> {}
        record Update<T>(int index, StableKey key, T item) implements Op<T> {}
        record Move<T>(int fromIndex, int toIndex, StableKey key) implements Op<T> {}
        record DuplicateKeyError<T>(StableKey key) implements Op<T> {}
    }

    public <T> List<Op<T>> diff(List<T> previous, List<T> next, Function<T, StableKey> keyExtractor) {
        List<Op<T>> ops = new ArrayList<>();
        Set<StableKey> seenNext = new HashSet<>();
        Map<StableKey, Integer> prevIndices = new HashMap<>();

        for (int i = 0; i < previous.size(); i++) {
            prevIndices.put(keyExtractor.apply(previous.get(i)), i);
        }

        Set<StableKey> nextKeys = new HashSet<>();
        for (T item : next) {
            StableKey key = keyExtractor.apply(item);
            if (!seenNext.add(key)) {
                ops.add(new Op.DuplicateKeyError<>(key));
                return ops;
            }
            nextKeys.add(key);
        }

        for (int i = previous.size() - 1; i >= 0; i--) {
            StableKey key = keyExtractor.apply(previous.get(i));
            if (!nextKeys.contains(key)) {
                ops.add(new Op.Remove<>(i, key));
            }
        }

        for (int i = 0; i < next.size(); i++) {
            T nextItem = next.get(i);
            StableKey key = keyExtractor.apply(nextItem);
            Integer prevIndex = prevIndices.get(key);
            if (prevIndex == null) {
                ops.add(new Op.Insert<>(i, key, nextItem));
            } else {
                if (!previous.get(prevIndex).equals(nextItem)) {
                    ops.add(new Op.Update<>(i, key, nextItem));
                }
                if (prevIndex != i) {
                    ops.add(new Op.Move<>(prevIndex, i, key));
                }
            }
        }
        return ops;
    }

    public static void registerAll(ComponentRegistry registry) {
        registry.register(ComponentDescriptor.builder("List")
                .role(SemanticRole.LIST)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.TEMPLATE)
                .requiredProp("items", List.class)
                .requiredProp("key", String.class)
                .prop("virtualized", Boolean.class, "true")
                .prop("estimatedItemHeight", Double.class, "56")
                .prop("overscan", Integer.class, "4")
                .prop("emptyMessage", String.class, "")
                .event("onScroll", CommonEvents.ScrollEvent.class)
                .build());
    }
}
