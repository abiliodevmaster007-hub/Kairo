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
    }

    private final List<T> items = new ArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Mutation<T>>> listeners = new CopyOnWriteArrayList<>();

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
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    public int size() {
        State.recordRead(this);
        return items.size();
    }

    public boolean isEmpty() {
        State.recordRead(this);
        return items.isEmpty();
    }

    public T get(int index) {
        State.recordRead(this);
        return items.get(index);
    }

    public void add(T item) {
        int index = items.size();
        items.add(item);
        emit(new Mutation.Added<>(index, item));
    }

    public void add(int index, T item) {
        items.add(index, item);
        emit(new Mutation.Added<>(index, item));
    }

    public void removeAt(int index) {
        T removed = items.remove(index);
        emit(new Mutation.Removed<>(index, removed));
    }

    public boolean remove(T item) {
        int idx = items.indexOf(item);
        if (idx < 0) return false;
        removeAt(idx);
        return true;
    }

    public void update(int index, T newItem) {
        T prev = items.set(index, newItem);
        emit(new Mutation.Updated<>(index, prev, newItem));
    }

    public void move(int fromIndex, int toIndex) {
        T item = items.remove(fromIndex);
        items.add(toIndex, item);
        emit(new Mutation.Moved<>(fromIndex, toIndex, item));
    }

    public void setAll(List<T> nextItems) {
        List<T> previous = List.copyOf(items);
        items.clear();
        items.addAll(nextItems);
        emit(new Mutation.ReplacedAll<>(previous, List.copyOf(items)));
    }

    public void clear() {
        items.clear();
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

    private void emit(Mutation<T> mutation) {
        State.TransactionBuffer tx = State.currentTransaction();
        if (tx != null) {
            for (Consumer<Mutation<T>> listener : listeners) {
                tx.scheduleCallback(this, listener, () -> listener.accept(mutation));
            }
            return;
        }
        for (Consumer<Mutation<T>> listener : listeners) {
            listener.accept(mutation);
        }
    }
}
