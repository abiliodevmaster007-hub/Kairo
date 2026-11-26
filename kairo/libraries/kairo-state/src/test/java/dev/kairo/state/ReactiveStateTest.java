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
}
