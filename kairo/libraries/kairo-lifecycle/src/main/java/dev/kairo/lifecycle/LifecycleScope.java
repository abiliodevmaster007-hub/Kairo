package dev.kairo.lifecycle;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical lifecycle container owning mount-scoped and lifetime-scoped disposables,
 * lifecycle hooks, and child scopes so unmount/destroy never leaks listeners or async jobs.
 */
public final class LifecycleScope {
    private LifecycleState state = LifecycleState.CREATED;
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
        for (int i = children.size() - 1; i >= 0; i--) {
            try {
                children.get(i).onUnmount();
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
        for (int i = mountDisposables.size() - 1; i >= 0; i--) {
            try {
                mountDisposables.get(i).dispose();
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
        for (int i = children.size() - 1; i >= 0; i--) {
            try {
                children.get(i).onDestroy();
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
        for (int i = lifetimeDisposables.size() - 1; i >= 0; i--) {
            try {
                lifetimeDisposables.get(i).dispose();
            } catch (RuntimeException ex) {
                firstFailure = collectFailure(firstFailure, ex);
            }
        }
        lifetimeDisposables.clear();
        mountHooks.clear();
        unmountHooks.clear();
        destroyHooks.clear();
        state = LifecycleState.DESTROYED;
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

    public <T extends Disposable> T ownForLifetime(T disposable) {
        if (disposable == null) return null;
        if (state == LifecycleState.DESTROYED) {
            disposable.dispose();
            return disposable;
        }
        lifetimeDisposables.add(disposable);
        return disposable;
    }

    private static List<Runnable> reverseCopy(List<Runnable> hooks) {
        List<Runnable> rev = new ArrayList<>(hooks.size());
        for (int i = hooks.size() - 1; i >= 0; i--) {
            rev.add(hooks.get(i));
        }
        return rev;
    }

    private static RuntimeException collectFailure(RuntimeException first, RuntimeException next) {
        if (first == null) return next;
        first.addSuppressed(next);
        return first;
    }
}
