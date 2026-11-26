package dev.kairo.components.list;

import java.util.Collections;
import java.util.List;

/**
 * Pure platform-neutral viewport virtualization window calculator for 10,000+ item lists.
 * Ensures only visible items + overscan buffer are materialized in the VirtualNode tree.
 */
public final class ListWindow {
    public record WindowRange(
            int startIndex,
            int endIndex,
            double topSpacerHeight,
            double bottomSpacerHeight,
            double totalContentHeight
    ) {
        public boolean isEmpty() {
            return endIndex < startIndex;
        }

        public int visibleCount() {
            return isEmpty() ? 0 : (endIndex - startIndex + 1);
        }

        public <T> List<T> slice(List<T> allItems) {
            if (isEmpty() || allItems == null || allItems.isEmpty()) {
                return Collections.emptyList();
            }
            int safeStart = Math.max(0, Math.min(startIndex, allItems.size() - 1));
            int safeEnd = Math.max(safeStart, Math.min(endIndex, allItems.size() - 1));
            return allItems.subList(safeStart, safeEnd + 1);
        }
    }

    private ListWindow() {}

    public static WindowRange compute(
            int totalItems,
            double estimatedItemHeight,
            double scrollOffsetY,
            double viewportHeight,
            int overscanCount) {
        if (totalItems <= 0) {
            return new WindowRange(0, -1, 0, 0, 0);
        }
        double itemH = Math.max(1.0, estimatedItemHeight);
        double totalH = totalItems * itemH;
        double maxOffset = Math.max(0.0, totalH - Math.max(0.0, viewportHeight));
        double clampedOffset = Math.max(0.0, Math.min(maxOffset, scrollOffsetY));

        int firstVisible = (int) Math.floor(clampedOffset / itemH);
        int lastVisible = (int) Math.ceil((clampedOffset + Math.max(1.0, viewportHeight)) / itemH) - 1;
        int overscan = Math.max(0, overscanCount);

        int start = Math.max(0, firstVisible - overscan);
        int end = Math.min(totalItems - 1, Math.max(start, lastVisible + overscan));

        double topSpacer = start * itemH;
        double bottomSpacer = Math.max(0.0, (totalItems - 1 - end) * itemH);
        return new WindowRange(start, end, topSpacer, bottomSpacer, totalH);
    }
}
