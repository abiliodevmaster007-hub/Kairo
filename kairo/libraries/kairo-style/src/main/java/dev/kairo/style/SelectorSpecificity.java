package dev.kairo.style;

/**
 * Exact three-tuple specificity (IDs > classes/states > types) without base-10 overflow bugs.
 * Ten classes (0, 10, 0) never outrank one ID (1, 0, 0).
 */
public record SelectorSpecificity(int ids, int classesAndStates, int types)
        implements Comparable<SelectorSpecificity> {

    public static final SelectorSpecificity ZERO = new SelectorSpecificity(0, 0, 0);

    public SelectorSpecificity add(SelectorSpecificity other) {
        return new SelectorSpecificity(
                this.ids + other.ids,
                this.classesAndStates + other.classesAndStates,
                this.types + other.types
        );
    }

    @Override
    public int compareTo(SelectorSpecificity other) {
        int cmp = Integer.compare(this.ids, other.ids);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.classesAndStates, other.classesAndStates);
        if (cmp != 0) return cmp;
        return Integer.compare(this.types, other.types);
    }
}
