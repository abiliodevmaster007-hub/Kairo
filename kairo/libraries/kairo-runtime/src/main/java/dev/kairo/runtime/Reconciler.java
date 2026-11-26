package dev.kairo.runtime;

import dev.kairo.core.KairoId;
import dev.kairo.renderer.api.RenderPatch;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Keyed and unkeyed VirtualNode tree reconciler producing minimal ordered RenderPatches
 * including MoveChild for reordered StableKey lists and null prop removals.
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
        List<VirtualNode> working = new ArrayList<>(prevChildren);
        Set<VirtualNode> matchedPrevSet = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        List<VirtualNode> matchedPrevForNext = new ArrayList<>(nextChildren.size());

        // Pass 1: Match each next child to an existing prev child (StableKey -> KairoId/logicalId -> unkeyed positional)
        for (int nextIdx = 0; nextIdx < nextChildren.size(); nextIdx++) {
            VirtualNode nextChild = nextChildren.get(nextIdx);
            VirtualNode match = findMatchingPrev(nextChild, nextIdx, prevChildren, matchedPrevSet);
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

        // Pass 3: Reorder survivors via MoveChild, insert new nodes, and recursively diff matched nodes
        for (int targetIdx = 0; targetIdx < nextChildren.size(); targetIdx++) {
            VirtualNode nextChild = nextChildren.get(targetIdx);
            VirtualNode matchedPrev = matchedPrevForNext.get(targetIdx);

            if (matchedPrev == null) {
                emitMountSubtree(nextChild, patches);
                patches.add(new RenderPatch.InsertChild(parentId, nextChild.id(), targetIdx));
                working.add(targetIdx, nextChild);
            } else {
                int currentIdx = indexOfById(working, matchedPrev.id());
                if (currentIdx != targetIdx) {
                    patches.add(new RenderPatch.MoveChild(parentId, matchedPrev.id(), currentIdx, targetIdx));
                    VirtualNode moved = working.remove(currentIdx);
                    working.add(targetIdx, moved);
                }
                reconcileNode(matchedPrev, nextChild, patches);
            }
        }
    }

    private VirtualNode findMatchingPrev(
            VirtualNode nextChild,
            int nextIdx,
            List<VirtualNode> prevChildren,
            Set<VirtualNode> alreadyMatched) {
        if (nextChild.stableKey() != null) {
            for (VirtualNode candidate : prevChildren) {
                if (!alreadyMatched.contains(candidate)
                        && nextChild.stableKey().equals(candidate.stableKey())
                        && nextChild.componentType().equals(candidate.componentType())) {
                    return candidate;
                }
            }
            return null;
        }

        if (nextChild.logicalId() != null) {
            for (VirtualNode candidate : prevChildren) {
                if (!alreadyMatched.contains(candidate)
                        && nextChild.logicalId().equals(candidate.logicalId())
                        && nextChild.componentType().equals(candidate.componentType())) {
                    return candidate;
                }
            }
            return null;
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

    private static int indexOfById(List<VirtualNode> list, KairoId id) {
        for (int i = 0; i < list.size(); i++) {
            if (Objects.equals(list.get(i).id(), id)) {
                return i;
            }
        }
        return -1;
    }
}
