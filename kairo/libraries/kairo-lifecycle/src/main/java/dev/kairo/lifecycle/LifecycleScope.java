package dev.kairo.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical lifecycle container owning mount-scoped and lifetime-scoped disposables,
 * lifecycle hooks, and child scopes so unmount/destroy never leaks listeners or async jobs.
 */
public final class LifecycleScope {
    private LifecycleState state = LifecycleState.CREATED;
    private LifecycleScope parent;
    private final List<Disposable> mountDisposables = new ArrayList<>();
    private final List<Disposable> lifetimeDisposables = new ArrayList<>();
    private final List<LifecycleScope> children = new ArrayList<>();
    private final List<Runnable> mountHooks = new ArrayList<>();
    private final List<Runnable> unmountHooks = new ArrayList<>();
    private final List<Runnable> destroyHooks = new ArrayList<>();

    public LifecycleState state() {
        return state;
    }

    public boolean isMounted() {
        return state == LifecycleState.MOUNTED;
    }

    public boolean isDestroyed() {
        return state == LifecycleState.DESTROYED;
    }

    public int activeMountDisposableCount() {
        int total = mountDisposables.size();
        for (LifecycleScope child : children) {
            total += child.activeMountDisposableCount();
        }
        return total;
    }

    public int childScopeCount() {
        return children.size();
    }

    public void onMount() {
        if (state == LifecycleState.DESTROYED) {
            throw new IllegalStateException("Cannot mount a destroyed LifecycleScope");
        }
        if (state == LifecycleState.MOUNTED) {
            return;
        }
        state = LifecycleState.MOUNTED;
        for (Runnable hook : List.copyOf(mountHooks)) {
            hook.run();
        }
        for (LifecycleScope child : List.copyOf(children)) {
            if (!child.isDestroyed() && child.state() != LifecycleState.MOUNTED) {
                child.onMount();
            }
        }
    }

    public void onUnmount() {
        if (state == LifecycleState.DESTROYED || state == LifecycleState.UNMOUNTED) {
            return;
        }
        RuntimeException firstFailure = null;
        for (LifecycleScope child : reverseCopy(children)) {
            try {
                child.onUnmount();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        for (Runnable hook : reverseCopy(unmountHooks)) {
            try {
                hook.run();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        for (Disposable disposable : reverseCopy(mountDisposables)) {
            try {
                disposable.dispose();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        mountDisposables.clear();
        state = LifecycleState.UNMOUNTED;
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public void onDestroy() {
        if (state == LifecycleState.DESTROYED) {
            return;
        }
        RuntimeException firstFailure = null;
        try {
            if (state == LifecycleState.MOUNTED) {
                onUnmount();
            }
        } catch (RuntimeException ex) {
            firstFailure = collectFailure(firstFailure, ex);
        }
        for (LifecycleScope child : reverseCopy(children)) {
            try {
                child.onDestroy();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        children.clear();
        for (Runnable hook : reverseCopy(destroyHooks)) {
            try {
                hook.run();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        for (Disposable disposable : reverseCopy(lifetimeDisposables)) {
            try {
                disposable.dispose();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        lifetimeDisposables.clear();
        mountHooks.clear();
        unmountHooks.clear();
        destroyHooks.clear();
        state = LifecycleState.DESTROYED;
        LifecycleScope p = this.parent;
        this.parent = null;
        if (p != null) {
            p.children.remove(this);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public LifecycleScope createChildScope() {
        LifecycleScope child = new LifecycleScope();
        if (state == LifecycleState.DESTROYED) {
            child.onDestroy();
            return child;
        }
        child.parent = this;
        children.add(child);
        if (state == LifecycleState.MOUNTED) {
            child.onMount();
        }
        return child;
    }

    public void onMount(Runnable hook) {
        if (state == LifecycleState.DESTROYED) return;
        mountHooks.add(hook);
        if (state == LifecycleState.MOUNTED) {
            hook.run();
        }
    }

    public void onUnmount(Runnable hook) {
        if (state == LifecycleState.DESTROYED) return;
        unmountHooks.add(hook);
    }

    public void onDestroy(Runnable hook) {
        if (state == LifecycleState.DESTROYED) {
            hook.run();
            return;
        }
        destroyHooks.add(hook);
    }

    public <T extends Disposable> T ownForMount(T disposable) {
        if (disposable == null) return null;
        if (state == LifecycleState.DESTROYED) {
            disposable.dispose();
            return disposable;
        }
        mountDisposables.add(disposable);
        return disposable;
    }

    public boolean releaseMountDisposable(Disposable disposable) {
        if (disposable == null) return false;
        return mountDisposables.remove(disposable);
    }

    public <T extends Disposable> T ownForLifetime(T disposable) {
        if (disposable == null) return null;
        if (state == LifecycleState.DESTROYED) {
            disposable.dispose();
            return disposable;
        }
        lifetimeDisposables.add(disposable);
        return disposable;
    }

    private static <E> List<E> reverseCopy(List<E> items) {
        List<E> rev = new ArrayList<>(items.size());
        for (int i = items.size() - 1; i >= 0; i--) {
            rev.add(items.get(i));
        }
        return rev;
    }

    private static RuntimeException collectFailure(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
}
