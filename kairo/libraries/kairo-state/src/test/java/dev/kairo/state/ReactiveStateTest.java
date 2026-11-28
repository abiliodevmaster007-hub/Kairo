package dev.kairo.state;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ReactiveStateTest {
    @Test
    void transactionCoalescesListenersAndAvoidsDerivedGlitches() {
        State<String> first = State.of("João");
        State<String> last = State.of("Silva");
        AtomicInteger deriveComputations = new AtomicInteger();
        Derived<String> full = Derived.derive(() -> {
            deriveComputations.incrementAndGet();
            return first.get() + " " + last.get();
        });

        List<String> observed = new ArrayList<>();
        full.subscribe(observed::add);
        int initialCount = deriveComputations.get();

        State.transaction(() -> {
            first.set("Ana");
            first.set("Maria");
            last.set("Costa");
        });

        assertThat(full.get()).isEqualTo("Maria Costa");
        assertThat(deriveComputations.get()).isEqualTo(initialCount + 1);
        assertThat(observed).containsExactly("Maria Costa");
        full.dispose();
    }

    @Test
    void nestedDerivedChainsTrackOuterDependenciesWithoutClobberingStack() {
        State<Integer> base = State.of(2);
        State<Integer> bonus = State.of(10);
        Derived<Integer> doubled = Derived.derive(() -> base.get() * 2);
        Derived<Integer> total = Derived.derive(() -> doubled.get() + bonus.get());

        assertThat(total.get()).isEqualTo(14);
        base.set(5);
        assertThat(total.get()).isEqualTo(20);
        bonus.set(100);
        assertThat(total.get()).isEqualTo(110);

        total.dispose();
        doubled.dispose();
    }

    @Test
    void observableListTriggersDerivedUpdatesAutomatically() {
        ObservableList<String> items = ObservableList.of(List.of("A", "B"));
        Derived<Integer> count = Derived.derive(items::size);

        assertThat(count.get()).isEqualTo(2);
        items.add("C");
        assertThat(count.get()).isEqualTo(3);
        items.setAll(List.of("X"));
        assertThat(count.get()).isEqualTo(1);
        count.dispose();
    }

    @Test
    void cyclicDerivedIsRejectedExplicitly() {
        AtomicReference<Derived<Integer>> refA = new AtomicReference<>();
        AtomicReference<Derived<Integer>> refB = new AtomicReference<>();
        State<Integer> trigger = State.of(0);

        refA.set(Derived.derive(() -> trigger.get() == 0 ? 1 : refB.get().get() + 1));
        refB.set(Derived.derive(() -> refA.get().get() + 1));

        assertThatThrownBy(() -> trigger.set(1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cyclic Derived");
    }

    @Test
    void untrackedReadsAndLoopGuardPreventFeedback() {
        State<Integer> tracked = State.of(1);
        State<Integer> ignored = State.of(10);
        Derived<Integer> d = Derived.derive(() -> tracked.get() + State.untracked(ignored::get));

        assertThat(d.get()).isEqualTo(11);
        ignored.set(99);
        assertThat(d.get()).isEqualTo(11);

        TwoWayLoopGuard guard = new TwoWayLoopGuard();
        AtomicInteger reentrantCalls = new AtomicInteger();
        guard.runFromView(() -> {
            assertThat(guard.activeOrigin()).isEqualTo(TwoWayLoopGuard.Origin.VIEW);
            guard.runFromModel(reentrantCalls::incrementAndGet);
        });
        assertThat(reentrantCalls.get()).isZero();
        assertThat(guard.activeOrigin()).isEqualTo(TwoWayLoopGuard.Origin.NONE);
        d.dispose();
    }

    @Test
    void observableListDeliversOrderedBatchInsideTransactionAndDirectMutationOutside() {
        ObservableList<String> list = ObservableList.of();
        ObservableList<Integer> numbers = ObservableList.of();
        List<ObservableList.Mutation<String>> listEvents = new ArrayList<>();
        List<ObservableList.Mutation<Integer>> numberEvents = new ArrayList<>();
        list.subscribe(listEvents::add);
        numbers.subscribe(numberEvents::add);

        // Outside explicit transaction -> delivers direct granular mutation
        list.add("zero");
        assertThat(listEvents).containsExactly(new ObservableList.Mutation.Added<>(0, "zero"));
        listEvents.clear();

        // Multiple mutations inside State.transaction -> delivers ordered Batch without dropping events
        State.transaction(() -> {
            list.add("one");
            list.add("two");
            list.removeAt(0);
            list.update(0, "ONE");
            list.add(0, "head");
            numbers.add(10);
            numbers.add(20);
        });

        assertThat(list.snapshot()).containsExactly("head", "ONE", "two");
        assertThat(listEvents).hasSize(1);
        assertThat(listEvents.get(0)).isInstanceOf(ObservableList.Mutation.Batch.class);
        var batch = (ObservableList.Mutation.Batch<String>) listEvents.get(0);
        assertThat(batch.mutations()).containsExactly(
                new ObservableList.Mutation.Added<>(1, "one"),
                new ObservableList.Mutation.Added<>(2, "two"),
                new ObservableList.Mutation.Removed<>(0, "zero"),
                new ObservableList.Mutation.Updated<>(0, "one", "ONE"),
                new ObservableList.Mutation.Added<>(0, "head")
        );

        assertThat(numberEvents).hasSize(1);
        assertThat(numberEvents.get(0)).isInstanceOf(ObservableList.Mutation.Batch.class);
        var numBatch = (ObservableList.Mutation.Batch<Integer>) numberEvents.get(0);
        assertThat(numBatch.mutations()).containsExactly(
                new ObservableList.Mutation.Added<>(0, 10),
                new ObservableList.Mutation.Added<>(1, 20)
        );
    }
}
