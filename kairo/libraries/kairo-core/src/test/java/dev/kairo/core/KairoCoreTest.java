package dev.kairo.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class KairoCoreTest {
    @Test
    void kairoIdGeneratesUniquePositiveSequence() {
        KairoId a = KairoId.next("login");
        KairoId b = KairoId.next("login");
        assertThat(a.value()).isPositive();
        assertThat(b.value()).isGreaterThan(a.value());
        assertThat(a.hasLogicalId()).isTrue();
    }

    @Test
    void stableKeyRejectsBlankValues() {
        assertThatThrownBy(() -> new StableKey("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(StableKey.of(42).value()).isEqualTo("42");
    }

    @Test
    void diagnosticBagTracksErrorsAndLocations() {
        Diagnostic.Bag bag = new Diagnostic.Bag();
        assertThat(bag.hasErrors()).isFalse();

        bag.warning("W01", "Unused style class", new SourceLocation("login.xml", 4, 12));
        assertThat(bag.hasErrors()).isFalse();

        bag.error("E01", "Duplicate ID", new SourceLocation("login.xml", 8, 5));
        assertThat(bag.hasErrors()).isTrue();
        assertThat(bag.all()).hasSize(2);
    }

    @Test
    void capabilityRegistryResolvesRegisteredCapabilities() {
        CapabilityRegistry registry = new CapabilityRegistry();
        CapabilityRegistry.Capability clipboard = new CapabilityRegistry.Capability() {
            @Override public String id() { return "clipboard"; }
            @Override public boolean isAvailable() { return true; }
        };
        registry.register(clipboard);

        assertThat(registry.isAvailable("clipboard")).isTrue();
        assertThat(registry.isAvailable("camera")).isFalse();
    }

    @Test
    void resultMapsOkAndPreservesErrors() {
        Result<Integer> ok = Result.ok(10).map(v -> v * 2);
        assertThat(ok.isOk()).isTrue();
        assertThat(((Result.Ok<Integer>) ok).value()).isEqualTo(20);

        Result<Integer> err = Result.<Integer>error("E_PARSE", "Invalid token", SourceLocation.UNKNOWN).map(v -> v * 2);
        assertThat(err.isOk()).isFalse();
    }
}
