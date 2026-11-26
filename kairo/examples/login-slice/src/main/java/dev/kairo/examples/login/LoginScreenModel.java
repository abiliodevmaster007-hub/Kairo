package dev.kairo.examples.login;

import dev.kairo.annotations.UIAction;
import dev.kairo.annotations.UIExpose;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.state.Derived;
import dev.kairo.state.State;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 100% platform-neutral Java 17 screen model for Stage 2 (A.1) Login Vertical Slice.
 * Contains zero android.* imports and runs identically on JVM TestKit and Android devices.
 */
public final class LoginScreenModel implements KairoRuntime.ScreenModel {
    @UIExpose public final State<String> email = State.of("");
    @UIExpose public final State<String> password = State.of("");
    @UIExpose public final State<String> feedback = State.of("");
    @UIExpose public final Derived<Boolean> canSubmit = Derived.derive(
            () -> !email.get().isBlank() && !password.get().isBlank()
    );

    private boolean authenticated;

    @UIAction
    public void submit() {
        String currentEmail = email.get().trim();
        if (!currentEmail.contains("@")) {
            feedback.set("Introduza um email válido.");
            authenticated = false;
            return;
        }
        if (password.get().length() < 6) {
            feedback.set("A palavra-passe precisa de pelo menos 6 caracteres.");
            authenticated = false;
            return;
        }
        authenticated = true;
        feedback.set("Sessão pronta para " + currentEmail);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public Map<String, Supplier<Object>> exposedBindings() {
        return Map.of(
                "email", () -> email,
                "password", () -> password,
                "feedback", () -> feedback,
                "canSubmit", () -> canSubmit
        );
    }

    @Override
    public Map<String, State<String>> twoWayStringStates() {
        return Map.of(
                "email", email,
                "password", password
        );
    }

    @Override
    public Map<String, Runnable> actions() {
        return Map.of("submit", this::submit);
    }
}
