package dev.kairo.state;

import dev.kairo.lifecycle.Disposable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Granular observable collection supporting add, insert, remove, update, move, clear, setAll,
 * and automatic Derived dependency tracking.
 */
public final class ObservableList<T> implements ReactiveSource {
    public sealed interface Mutation<T> {
        record Added<T>(int index, T item) implements Mutation<T> {}
        record Removed<T>(int index, T item) implements Mutation<T> {}
        record Updated<T>(int index, T previousItem, T newItem) implements Mutation<T> {}
        record Moved<T>(int fromIndex, int toIndex, T item) implements Mutation<T> {}
        record Cleared<T>() implements Mutation<T> {}
        record ReplacedAll<T>(List<T> previous, List<T> current) implements Mutation<T> {}
        record Batch<T>(List<Mutation<T>> mutations) implements Mutation<T> {
            public Batch {
                mutations = List.copyOf(mutations);
            }
        }
    }

    private final Object lock = new Object();
    private final List<T> items = new ArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Mutation<T>>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Derived<?>> derivedDependents = new CopyOnWriteArrayList<>();

    public static <T> ObservableList<T> of() {
        return new ObservableList<>();
    }

    public static <T> ObservableList<T> of(List<T> initial) {
        ObservableList<T> list = new ObservableList<>();
        list.items.addAll(initial);
        return list;
    }

    public List<T> snapshot() {
        State.recordRead(this);
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(items));
        }
    }

    public int size() {
        State.recordRead(this);
        synchronized (lock) {
            return items.size();
        }
    }

    public boolean isEmpty() {
        State.recordRead(this);
        synchronized (lock) {
            return items.isEmpty();
        }
    }

    public T get(int index) {
        State.recordRead(this);
        synchronized (lock) {
            return items.get(index);
        }
    }

    public void add(T item) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> add(item), "ObservableList.add")) {
            return;
        }
        int index;
        synchronized (lock) {
            index = items.size();
            items.add(item);
        }
        emit(new Mutation.Added<>(index, item));
    }

    public void add(int index, T item) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> add(index, item), "ObservableList.add")) {
            return;
        }
        synchronized (lock) {
            items.add(index, item);
        }
        emit(new Mutation.Added<>(index, item));
    }

    public void removeAt(int index) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> removeAt(index), "ObservableList.removeAt")) {
            return;
        }
        T removed;
        synchronized (lock) {
            removed = items.remove(index);
        }
        emit(new Mutation.Removed<>(index, removed));
    }

    public boolean remove(T item) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> remove(item), "ObservableList.remove")) {
            return false;
        }
        int idx;
        synchronized (lock) {
            idx = items.indexOf(item);
        }
        if (idx < 0) return false;
        removeAt(idx);
        return true;
    }

    public void update(int index, T newItem) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> update(index, newItem), "ObservableList.update")) {
            return;
        }
        T prev;
        synchronized (lock) {
            prev = items.set(index, newItem);
        }
        emit(new Mutation.Updated<>(index, prev, newItem));
    }

    public void move(int fromIndex, int toIndex) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> move(fromIndex, toIndex), "ObservableList.move")) {
            return;
        }
        T item;
        synchronized (lock) {
            item = items.remove(fromIndex);
            items.add(toIndex, item);
        }
        emit(new Mutation.Moved<>(fromIndex, toIndex, item));
    }

    public void setAll(List<T> nextItems) {
        List<T> safeNext = List.copyOf(nextItems);
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> setAll(safeNext), "ObservableList.setAll")) {
            return;
        }
        List<T> previous;
        synchronized (lock) {
            previous = List.copyOf(items);
            items.clear();
            items.addAll(safeNext);
        }
        emit(new Mutation.ReplacedAll<>(previous, safeNext));
    }

    public void clear() {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(this::clear, "ObservableList.clear")) {
            return;
        }
        synchronized (lock) {
            items.clear();
        }
        emit(new Mutation.Cleared<>());
    }

    public Disposable subscribe(Consumer<Mutation<T>> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Disposable subscribeAny(Runnable listener) {
        return subscribe(ignored -> listener.run());
    }

    Disposable subscribeDerived(Derived<?> dependent) {
        if (!derivedDependents.contains(dependent)) {
            derivedDependents.add(dependent);
        }
        return () -> derivedDependents.remove(dependent);
    }

    private void emit(Mutation<T> mutation) {
        if (derivedDependents.isEmpty() && listeners.isEmpty()) {
            return;
        }
        State.dispatchInTransactionOrImplicit(tx -> {
            for (Derived<?> dependent : derivedDependents) {
                tx.scheduleDerived(dependent);
            }
            if (!listeners.isEmpty()) {
                tx.scheduleListMutation(this, mutation);
            }
        });
    }

    @SuppressWarnings("unchecked")
    void deliverBatchUnchecked(List<Mutation<?>> rawMutations) {
        if (rawMutations == null || rawMutations.isEmpty() || listeners.isEmpty()) {
            return;
        }
        Mutation<T> payload;
        if (rawMutations.size() == 1) {
            payload = (Mutation<T>) rawMutations.get(0);
        } else {
            List<Mutation<T>> typed = new ArrayList<>(rawMutations.size());
            for (Mutation<?> mutation : rawMutations) {
                typed.add((Mutation<T>) mutation);
            }
            payload = new Mutation.Batch<>(typed);
        }
        for (Consumer<Mutation<T>> listener : listeners) {
            try {
                listener.accept(payload);
            } catch (RuntimeException ex) {
                ReactiveRuntimeConfig.reportListenerError("ObservableList.listener", ex);
            }
        }
    }
}
