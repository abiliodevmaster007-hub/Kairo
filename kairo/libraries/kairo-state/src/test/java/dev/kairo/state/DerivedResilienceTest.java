package dev.kairo.state;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class DerivedResilienceTest {

    @AfterEach
    void restoreDefaults() {
        ReactiveRuntimeConfig.resetDefaults();
    }

    @Test
    void preservesDependenciesAndValueWhenRecomputeFailsThenRecoversAutomatically() {
        List<String> derivedErrors = new ArrayList<>();
        ReactiveRuntimeConfig.setErrorHandler(new ReactiveErrorHandler() {
            @Override
            public void onListenerError(String source, Throwable error) {}

            @Override
            public void onDerivedError(Derived<?> derived, Throwable error) {
                derivedErrors.add(error.getMessage());
            }
        });

        State<Integer> price = State.of(10);
        State<Integer> quantity = State.of(2);
        Derived<Integer> total = Derived.derive(() -> {
            int q = quantity.get();
            if (q < 0) {
                throw new IllegalArgumentException("Negative quantity: " + q);
            }
            return price.get() * q;
        });

        assertThat(total.get()).isEqualTo(20);

        // Transient failure must preserve cachedValue=20, report onDerivedError, and keep price/quantity subscribed
        quantity.set(-1);
        assertThat(total.get()).isEqualTo(20);
        assertThat(derivedErrors).containsExactly("Negative quantity: -1");

        // Recovery via previously preserved dependency subscription
        quantity.set(5);
        assertThat(total.get()).isEqualTo(50);

        // Changing price also continues reacting
        price.set(12);
        assertThat(total.get()).isEqualTo(60);

        total.dispose();
    }

    @Test
    void diamondGraphRemainsGlitchFreeEvenOutsideExplicitTransaction() {
        State<Integer> source = State.of(1);
        Derived<Integer> left = Derived.derive(() -> source.get() * 2);
        Derived<Integer> right = Derived.derive(() -> source.get() * 10);

        AtomicInteger diamondComputations = new AtomicInteger();
        List<String> observedPairs = new ArrayList<>();
        Derived<String> joined = Derived.derive(() -> {
            diamondComputations.incrementAndGet();
            return left.get() + ":" + right.get();
        });
        joined.subscribe(observedPairs::add);
        int initialRuns = diamondComputations.get();

        // Single State.set without explicit transaction must still flush B and C before D
        source.set(3);
        assertThat(joined.get()).isEqualTo("6:30");
        assertThat(diamondComputations.get()).isEqualTo(initialRuns + 1);
        assertThat(observedPairs).containsExactly("6:30");

        joined.dispose();
        left.dispose();
        right.dispose();
    }

    @Test
    void reportsTransactionErrorsBeforePropagating() {
        List<String> txErrors = new ArrayList<>();
        ReactiveRuntimeConfig.setErrorHandler(new ReactiveErrorHandler() {
            @Override
            public void onListenerError(String source, Throwable error) {}

            @Override
            public void onTransactionError(Throwable error) {
                txErrors.add(error.getMessage());
            }
        });

        assertThatThrownBy(() -> State.transaction(() -> {
            throw new IllegalStateException("tx-failure");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("tx-failure");

        assertThat(txErrors).containsExactly("tx-failure");
    }

    @Test
    void subscribesNewlyDiscoveredBranchDependenciesWhenRecomputeThrowsAndCleansOldAfterRecovery() {
        List<String> derivedErrors = new ArrayList<>();
        ReactiveRuntimeConfig.setErrorHandler(new ReactiveErrorHandler() {
            @Override
            public void onListenerError(String source, Throwable error) {}

            @Override
            public void onDerivedError(Derived<?> derived, Throwable error) {
                derivedErrors.add(error.getMessage());
            }
        });

        State<Boolean> useAlt = State.of(false);
        State<Integer> mainValue = State.of(10);
        State<Integer> altValue = State.of(-5);
        AtomicInteger computations = new AtomicInteger();

        Derived<String> branched = Derived.derive(() -> {
            computations.incrementAndGet();
            if (useAlt.get()) {
                int v = altValue.get();
                if (v < 0) {
                    throw new IllegalArgumentException("Invalid alt: " + v);
                }
                return "alt:" + v;
            }
            return "main:" + mainValue.get();
        });

        assertThat(branched.get()).isEqualTo("main:10");

        // Switch branch and fail after reading altValue -> must preserve old value AND subscribe to altValue
        useAlt.set(true);
        assertThat(branched.get()).isEqualTo("main:10");
        assertThat(derivedErrors).containsExactly("Invalid alt: -5");

        // Fixing altValue without toggling useAlt must automatically recover Derived
        altValue.set(42);
        assertThat(branched.get()).isEqualTo("alt:42");

        // After successful recovery on alt branch, stale mainValue updates must not trigger recompute
        int runsAfterRecovery = computations.get();
        mainValue.set(999);
        assertThat(computations.get()).isEqualTo(runsAfterRecovery);
        assertThat(branched.get()).isEqualTo("alt:42");

        branched.dispose();
    }

    @Test
    void flushesCommittedMutationsToDerivedAndObserversWhenTransactionThrows() {
        List<String> txErrors = new ArrayList<>();
        ReactiveRuntimeConfig.setErrorHandler(new ReactiveErrorHandler() {
            @Override
            public void onListenerError(String source, Throwable error) {}

            @Override
            public void onTransactionError(Throwable error) {
                txErrors.add(error.getMessage());
            }
        });

        State<Integer> count = State.of(1);
        ObservableList<String> items = ObservableList.of();
        Derived<String> summary = Derived.derive(() -> count.get() + ":" + items.size());

        List<Integer> countEvents = new ArrayList<>();
        List<ObservableList.Mutation<String>> listEvents = new ArrayList<>();
        List<String> summaryEvents = new ArrayList<>();
        count.subscribe(countEvents::add);
        items.subscribe(listEvents::add);
        summary.subscribe(summaryEvents::add);

        assertThatThrownBy(() -> State.transaction(() -> {
            count.set(10);
            items.add("A");
            throw new IllegalStateException("partial-tx-failure");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("partial-tx-failure");

        assertThat(txErrors).containsExactly("partial-tx-failure");
        assertThat(count.get()).isEqualTo(10);
        assertThat(items.snapshot()).containsExactly("A");
        assertThat(summary.get()).isEqualTo("10:1");
        assertThat(countEvents).containsExactly(10);
        assertThat(listEvents).containsExactly(new ObservableList.Mutation.Added<>(0, "A"));
        assertThat(summaryEvents).containsExactly("10:1");

        summary.dispose();
    }
}
