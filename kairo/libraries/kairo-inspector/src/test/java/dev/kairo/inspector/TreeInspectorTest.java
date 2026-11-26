package dev.kairo.inspector;

import dev.kairo.core.KairoId;
import dev.kairo.core.StableKey;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.runtime.VirtualNode;
import dev.kairo.state.State;
import dev.kairo.style.ComputedStyle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

final class TreeInspectorTest {

    @Test
    void redactsPasswordsTokensPinsAndSensitivePropsWithoutLeakingSecrets() {
        VirtualNode passwordInput = new VirtualNode(
                KairoId.next("userPassword"),
                "Input",
                "userPassword",
                StableKey.of("pw"),
                Map.of("type", "password", "value", "SuperSecret123!"),
                ComputedStyle.empty(),
                Set.of(),
                Map.of(),
                List.of()
        );
        VirtualNode apiTokenNode = new VirtualNode(
                KairoId.next("apiToken"),
                "Input",
                "apiToken",
                null,
                Map.of("value", "sk_live_987654321", "sensitive", "true"),
                ComputedStyle.empty(),
                Set.of(),
                Map.of(),
                List.of()
        );
        VirtualNode safeNode = new VirtualNode(
                KairoId.next("email"),
                "Input",
                "email",
                null,
                Map.of("value", "dev@kairo.dev"),
                ComputedStyle.empty(),
                Set.of(),
                Map.of(),
                List.of()
        );

        assertThat(SecretRedactor.redactNodeProp(passwordInput, "value", "SuperSecret123!"))
                .isEqualTo(SecretRedactor.REDACTED_MASK);
        assertThat(SecretRedactor.redactNodeProp(apiTokenNode, "value", "sk_live_987654321"))
                .isEqualTo(SecretRedactor.REDACTED_MASK);
        assertThat(SecretRedactor.redactNodeProp(safeNode, "value", "dev@kairo.dev"))
                .isEqualTo("dev@kairo.dev");
    }

    @Test
    void dumpBindingsMasksSecretsAndExposesSafeFields() {
        State<String> email = State.of("ana@kairo.dev");
        State<String> authToken = State.of("secret_bearer_token_abc");
        State<String> password = State.of("hunter2");

        KairoRuntime.ScreenModel model = new KairoRuntime.ScreenModel() {
            @Override
            public Map<String, Supplier<Object>> exposedBindings() {
                return Map.of("email", () -> email, "authToken", () -> authToken);
            }

            @Override
            public Map<String, State<String>> twoWayStringStates() {
                return Map.of("email", email, "password", password);
            }

            @Override
            public Map<String, Runnable> actions() {
                return Map.of();
            }
        };

        String dump = new TreeInspector().dumpBindings(model);
        assertThat(dump).contains("email = ana@kairo.dev");
        assertThat(dump).contains("authToken = ••••••");
        assertThat(dump).contains("bind:password = ••••••");
        assertThat(dump).doesNotContain("secret_bearer_token_abc");
        assertThat(dump).doesNotContain("hunter2");
    }
}
