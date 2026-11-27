package dev.kairo.state;

import dev.kairo.lifecycle.Disposable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Reactive observable state container with nested stack dependency tracking,
 * glitch-free coalesced transactions, and bounded cycle protection.
 */
public final class State<T> implements ReactiveSource {
    private static final int MAX_FLUSH_ROUNDS = 100;
    private static final Set<ReactiveSource> UNTRACKED_SINK = Set.of();

    private static final ThreadLocal<Deque<Set<ReactiveSource>>> TRACKING_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<TransactionBuffer> ACTIVE_TRANSACTION = new ThreadLocal<>();

    static final class TransactionBuffer {
        final Set<State<?>> dirtyStates = new LinkedHashSet<>();
        final Set<Derived<?>> dirtyDeriveds = new LinkedHashSet<>();
        final Map<Object, Runnable> coalescedCallbacks = new LinkedHashMap<>();
        boolean flushing;

        void scheduleDerived(Derived<?> derived) {
            dirtyDeriveds.add(derived);
        }

        void scheduleCallback(Object owner, Object listener, Runnable callback) {
            coalescedCallbacks.put(new ListenerKey(owner, listener), callback);
        }
    }

    private record ListenerKey(Object owner, Object listener) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ListenerKey other)) return false;
            return owner == other.owner && listener == other.listener;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(owner) * 31 + System.identityHashCode(listener);
        }
    }

    private volatile T value;
    private final CopyOnWriteArrayList<Consumer<T>> listeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Derived<?>> derivedDependents = new CopyOnWriteArrayList<>();

    public State(T initialValue) {
        this.value = initialValue;
    }

    public static <T> State<T> of(T initialValue) {
        return new State<>(initialValue);
    }

    public T get() {
        recordRead(this);
        return value;
    }

    public T peek() {
        return value;
    }

    public void set(T nextValue) {
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> set(nextValue), "State.set")) {
            return;
        }
        if (Objects.equals(this.value, nextValue)) {
            return;
        }
        this.value = nextValue;
        TransactionBuffer tx = ACTIVE_TRANSACTION.get();
        if (tx != null) {
            tx.dirtyStates.add(this);
            return;
        }
        notifyImmediate();
    }

    public void update(UnaryOperator<T> updater) {
        Objects.requireNonNull(updater, "updater cannot be null");
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> update(updater), "State.update")) {
            return;
        }
        set(updater.apply(value));
    }

    public Disposable subscribe(Consumer<T> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public Disposable subscribeAny(Runnable listener) {
        Consumer<T> wrapper = ignored -> listener.run();
        return subscribe(wrapper);
    }

    Disposable subscribeDerived(Derived<?> dependent) {
        if (!derivedDependents.contains(dependent)) {
            derivedDependents.add(dependent);
        }
        return () -> derivedDependents.remove(dependent);
    }

    private void notifyImmediate() {
        for (Derived<?> dependent : derivedDependents) {
            dependent.onDependencyChanged();
        }
        T snapshot = this.value;
        for (Consumer<T> listener : listeners) {
            try {
                listener.accept(snapshot);
            } catch (RuntimeException ex) {
                ReactiveRuntimeConfig.reportListenerError("State.listener", ex);
            }
        }
    }

    private void flushToBuffer(TransactionBuffer tx) {
        for (Derived<?> dependent : derivedDependents) {
            tx.scheduleDerived(dependent);
        }
        for (Consumer<T> listener : listeners) {
            T snapshot = this.value;
            tx.scheduleCallback(this, listener, () -> listener.accept(snapshot));
        }
    }

    static void recordRead(ReactiveSource source) {
        Deque<Set<ReactiveSource>> stack = TRACKING_STACK.get();
        Set<ReactiveSource> current = stack.peek();
        if (current != null && current != UNTRACKED_SINK) {
            current.add(source);
        }
    }

    static void beginTracking() {
        TRACKING_STACK.get().push(new LinkedHashSet<>());
    }

    static Set<ReactiveSource> endTracking() {
        Deque<Set<ReactiveSource>> stack = TRACKING_STACK.get();
        Set<ReactiveSource> captured = stack.isEmpty() ? Set.of() : stack.pop();
        if (stack.isEmpty()) {
            TRACKING_STACK.remove();
        }
        return captured;
    }

    public static <T> T untracked(Supplier<T> supplier) {
        Deque<Set<ReactiveSource>> stack = TRACKING_STACK.get();
        stack.push(UNTRACKED_SINK);
        try {
            return supplier.get();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                TRACKING_STACK.remove();
            }
        }
    }

    static TransactionBuffer currentTransaction() {
        return ACTIVE_TRANSACTION.get();
    }

    public static void transaction(Runnable block) {
        Objects.requireNonNull(block, "block cannot be null");
        if (!ReactiveRuntimeConfig.checkUiThreadOrDispatch(() -> transaction(block), "State.transaction")) {
            return;
        }
        if (ACTIVE_TRANSACTION.get() != null) {
            block.run();
            return;
        }
        TransactionBuffer tx = new TransactionBuffer();
        ACTIVE_TRANSACTION.set(tx);
        try {
            block.run();
            flushTransaction(tx);
        } finally {
            ACTIVE_TRANSACTION.remove();
        }
    }

    private static void flushTransaction(TransactionBuffer tx) {
        if (tx.flushing) return;
        tx.flushing = true;
        try {
            int rounds = 0;
            while (!tx.dirtyStates.isEmpty() || !tx.dirtyDeriveds.isEmpty() || !tx.coalescedCallbacks.isEmpty()) {
                if (++rounds > MAX_FLUSH_ROUNDS) {
                    throw new IllegalStateException("Reactive transaction exceeded maximum flush rounds (" + MAX_FLUSH_ROUNDS + "); check for cyclic state updates.");
                }
                if (!tx.dirtyStates.isEmpty()) {
                    List<State<?>> batch = new ArrayList<>(tx.dirtyStates);
                    tx.dirtyStates.clear();
                    for (State<?> dirty : batch) {
                        dirty.flushToBuffer(tx);
                    }
                }
                if (!tx.dirtyDeriveds.isEmpty()) {
                    List<Derived<?>> derivedBatch = new ArrayList<>(tx.dirtyDeriveds);
                    tx.dirtyDeriveds.clear();
                    Set<Derived<?>> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
                    for (Derived<?> derived : derivedBatch) {
                        if (seen.add(derived)) {
                            derived.flushInTransaction(tx);
                        }
                    }
                }
                if (tx.dirtyStates.isEmpty() && tx.dirtyDeriveds.isEmpty() && !tx.coalescedCallbacks.isEmpty()) {
                    List<Runnable> callbacks = new ArrayList<>(tx.coalescedCallbacks.values());
                    tx.coalescedCallbacks.clear();
                    for (Runnable job : callbacks) {
                        try {
                            job.run();
                        } catch (RuntimeException ex) {
                            ReactiveRuntimeConfig.reportListenerError("State.transaction.listener", ex);
                        }
                    }
                }
            }
        } finally {
            tx.flushing = false;
        }
    }
}
