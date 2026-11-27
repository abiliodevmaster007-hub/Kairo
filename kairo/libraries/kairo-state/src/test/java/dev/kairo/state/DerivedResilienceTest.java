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
}
