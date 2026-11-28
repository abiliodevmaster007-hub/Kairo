package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.renderer.api.RenderPatch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Keyed and unkeyed VirtualNode tree reconciler producing minimal ordered RenderPatches
 * using O(1) StableKey and logicalId indexing plus MoveChild for reordered lists.
 */
public final class Reconciler {
    public List<RenderPatch> diff(VirtualNode prev, VirtualNode next) {
        List<RenderPatch> patches = new ArrayList<>();
        if (prev == null && next == null) return patches;
        if (prev == null) {
            emitMountSubtree(next, patches);
            return patches;
        }
        if (next == null) {
            patches.add(new RenderPatch.RemoveNode(prev.id()));
            return patches;
        }
        if (!Objects.equals(prev.id(), next.id()) || !prev.componentType().equals(next.componentType())) {
            patches.add(new RenderPatch.RemoveNode(prev.id()));
            emitMountSubtree(next, patches);
            return patches;
        }
        reconcileNode(prev, next, patches);
        return patches;
    }

    private void emitMountSubtree(VirtualNode node, List<RenderPatch> patches) {
        patches.add(new RenderPatch.CreateNode(node.id(), node.componentType(), node.props(), node.style()));
        if (!node.visualStates().isEmpty()) {
            patches.add(new RenderPatch.UpdateVisualStates(node.id(), node.visualStates()));
        }
        patches.add(new RenderPatch.UpdateBounds(node.id(), node.bounds()));
        for (int i = 0; i < node.children().size(); i++) {
            VirtualNode child = node.children().get(i);
            emitMountSubtree(child, patches);
            patches.add(new RenderPatch.InsertChild(node.id(), child.id(), i));
        }
    }

    private void reconcileNode(VirtualNode prev, VirtualNode next, List<RenderPatch> patches) {
        for (var entry : next.props().entrySet()) {
            String prevVal = prev.props().get(entry.getKey());
            if (!Objects.equals(prevVal, entry.getValue())) {
                patches.add(new RenderPatch.UpdateProp(next.id(), entry.getKey(), entry.getValue()));
            }
        }
        for (String prevKey : prev.props().keySet()) {
            if (!prevKey.startsWith("__") && !next.props().containsKey(prevKey)) {
                patches.add(new RenderPatch.UpdateProp(next.id(), prevKey, null));
            }
        }
        if (!Objects.equals(prev.style(), next.style())) {
            patches.add(new RenderPatch.UpdateStyle(next.id(), next.style()));
        }
        if (!Objects.equals(prev.visualStates(), next.visualStates())) {
            patches.add(new RenderPatch.UpdateVisualStates(next.id(), next.visualStates()));
        }
        if (!Objects.equals(prev.bounds(), next.bounds())) {
            patches.add(new RenderPatch.UpdateBounds(next.id(), next.bounds()));
        }

        reconcileChildren(next.id(), prev.children(), next.children(), patches);
    }

    private void reconcileChildren(
            KairoId parentId,
            List<VirtualNode> prevChildren,
            List<VirtualNode> nextChildren,
            List<RenderPatch> patches) {
        if (prevChildren.isEmpty()) {
            for (int i = 0; i < nextChildren.size(); i++) {
                VirtualNode nextChild = nextChildren.get(i);
                emitMountSubtree(nextChild, patches);
                patches.add(new RenderPatch.InsertChild(parentId, nextChild.id(), i));
            }
            return;
        }
        if (nextChildren.isEmpty()) {
            for (int i = prevChildren.size() - 1; i >= 0; i--) {
                patches.add(new RenderPatch.RemoveNode(prevChildren.get(i).id()));
            }
            return;
        }

        Map<StableKey, Object> prevByKey = new HashMap<>(prevChildren.size() * 2);
        Map<String, Object> prevByLogicalId = new HashMap<>(prevChildren.size() * 2);
        for (VirtualNode prevChild : prevChildren) {
            if (prevChild.stableKey() != null) {
                indexCandidate(prevByKey, prevChild.stableKey(), prevChild);
            } else if (prevChild.logicalId() != null) {
                indexCandidate(prevByLogicalId, prevChild.logicalId(), prevChild);
            }
        }

        List<VirtualNode> working = new ArrayList<>(prevChildren);
        Set<VirtualNode> matchedPrevSet = java.util.Collections.newSetFromMap(new IdentityHashMap<>(nextChildren.size() * 2));
        List<VirtualNode> matchedPrevForNext = new ArrayList<>(nextChildren.size());

        // Pass 1: O(1) key/logicalId matching + positional fallback
        for (int nextIdx = 0; nextIdx < nextChildren.size(); nextIdx++) {
            VirtualNode nextChild = nextChildren.get(nextIdx);
            VirtualNode match = findMatchingPrev(nextChild, nextIdx, prevChildren, prevByKey, prevByLogicalId, matchedPrevSet);
            if (match != null) {
                matchedPrevSet.add(match);
            }
            matchedPrevForNext.add(match);
        }

        // Pass 2: Remove unmatched previous children in reverse index order
        for (int i = working.size() - 1; i >= 0; i--) {
            VirtualNode prevChild = working.get(i);
            if (!matchedPrevSet.contains(prevChild)) {
                patches.add(new RenderPatch.RemoveNode(prevChild.id()));
                working.remove(i);
            }
        }

        // Build O(1) index map for remaining survivors
        Map<KairoId, Integer> workingIndexById = new HashMap<>(Math.max(16, nextChildren.size() * 2));
        for (int i = 0; i < working.size(); i++) {
            workingIndexById.put(working.get(i).id(), i);
        }

        // Pass 3: Reorder survivors via MoveChild, insert new nodes, and recursively diff matched nodes
        for (int targetIdx = 0; targetIdx < nextChildren.size(); targetIdx++) {
            VirtualNode nextChild = nextChildren.get(targetIdx);
            VirtualNode matchedPrev = matchedPrevForNext.get(targetIdx);

            if (matchedPrev == null) {
                emitMountSubtree(nextChild, patches);
                patches.add(new RenderPatch.InsertChild(parentId, nextChild.id(), targetIdx));
                boolean isAppend = targetIdx == working.size();
                working.add(targetIdx, nextChild);
                if (isAppend) {
                    workingIndexById.put(nextChild.id(), targetIdx);
                } else {
                    refreshWorkingIndices(working, workingIndexById, targetIdx, working.size() - 1);
                }
            } else {
                int currentIdx;
                if (targetIdx < working.size() && Objects.equals(working.get(targetIdx).id(), matchedPrev.id())) {
                    currentIdx = targetIdx;
                } else {
                    Integer mapped = workingIndexById.get(matchedPrev.id());
                    currentIdx = mapped != null ? mapped : -1;
                }
                if (currentIdx != targetIdx && currentIdx >= 0) {
                    patches.add(new RenderPatch.MoveChild(parentId, matchedPrev.id(), currentIdx, targetIdx));
                    VirtualNode moved = working.remove(currentIdx);
                    working.add(targetIdx, moved);
                    int lo = Math.min(currentIdx, targetIdx);
                    int hi = Math.max(currentIdx, targetIdx);
                    refreshWorkingIndices(working, workingIndexById, lo, hi);
                }
                reconcileNode(matchedPrev, nextChild, patches);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <K> void indexCandidate(Map<K, Object> index, K key, VirtualNode node) {
        Object existing = index.get(key);
        if (existing == null) {
            index.put(key, node);
        } else if (existing instanceof VirtualNode first) {
            Deque<VirtualNode> queue = new ArrayDeque<>(4);
            queue.addLast(first);
            queue.addLast(node);
            index.put(key, queue);
        } else {
            ((Deque<VirtualNode>) existing).addLast(node);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K> VirtualNode pollCandidate(
            Map<K, Object> index,
            K key,
            String componentType,
            Set<VirtualNode> alreadyMatched) {
        Object entry = index.get(key);
        if (entry == null) {
            return null;
        }
        if (entry instanceof VirtualNode single) {
            if (!alreadyMatched.contains(single) && componentType.equals(single.componentType())) {
                index.remove(key);
                return single;
            }
            return null;
        }
        Deque<VirtualNode> queue = (Deque<VirtualNode>) entry;
        int checked = 0;
        int size = queue.size();
        while (checked < size) {
            VirtualNode candidate = queue.pollFirst();
            checked++;
            if (candidate == null) break;
            if (!alreadyMatched.contains(candidate) && componentType.equals(candidate.componentType())) {
                if (queue.isEmpty()) {
                    index.remove(key);
                }
                return candidate;
            }
            queue.addLast(candidate);
        }
        return null;
    }

    private VirtualNode findMatchingPrev(
            VirtualNode nextChild,
            int nextIdx,
            List<VirtualNode> prevChildren,
            Map<StableKey, Object> prevByKey,
            Map<String, Object> prevByLogicalId,
            Set<VirtualNode> alreadyMatched) {
        if (nextChild.stableKey() != null) {
            return pollCandidate(prevByKey, nextChild.stableKey(), nextChild.componentType(), alreadyMatched);
        }
        if (nextChild.logicalId() != null) {
            return pollCandidate(prevByLogicalId, nextChild.logicalId(), nextChild.componentType(), alreadyMatched);
        }
        if (nextIdx < prevChildren.size()) {
            VirtualNode positional = prevChildren.get(nextIdx);
            if (!alreadyMatched.contains(positional)
                    && positional.stableKey() == null
                    && positional.logicalId() == null
                    && positional.componentType().equals(nextChild.componentType())) {
                return positional;
            }
        }
        return null;
    }

    private static void refreshWorkingIndices(
            List<VirtualNode> working,
            Map<KairoId, Integer> indexMap,
            int fromInclusive,
            int toInclusive) {
        for (int i = fromInclusive; i <= toInclusive; i++) {
            indexMap.put(working.get(i).id(), i);
        }
    }
}
