package dev.kairo.state;

/**
 * Prevents infinite feedback loops in two-way bindings (e.g., Android EditText TextWatcher vs programmatic State updates).
 */
public final class TwoWayLoopGuard {
    public enum Origin {
        NONE,
        MODEL,
        VIEW
    }

    private Origin activeOrigin = Origin.NONE;

    public Origin activeOrigin() {
        return activeOrigin;
    }

    public void runFromModel(Runnable action) {
        if (activeOrigin != Origin.NONE) return;
        activeOrigin = Origin.MODEL;
        try {
            action.run();
        } finally {
            activeOrigin = Origin.NONE;
        }
    }

    public void runFromView(Runnable action) {
        if (activeOrigin != Origin.NONE) return;
        activeOrigin = Origin.VIEW;
        try {
            action.run();
        } finally {
            activeOrigin = Origin.NONE;
        }
    }
}
