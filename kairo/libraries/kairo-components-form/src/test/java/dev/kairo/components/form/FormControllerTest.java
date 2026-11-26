package dev.kairo.components.form;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

final class FormControllerTest {
    @Test
    void validatesFieldsByPolicyMatchesPasswordsAndRedactsSecrets() {
        FormController form = new FormController(FormController.ValidateOn.CHANGE);
        var email = form.field("email");
        email.addValidator(FormController.Validators.compose(
                FormController.Validators.required("Email obrigatório"),
                FormController.Validators.email("Email inválido")
        ));

        var password = form.field("password").sensitive(true);
        password.addValidator(FormController.Validators.minLength(8, "Mínimo 8 caracteres"));

        var confirm = form.field("confirm").sensitive(true);
        confirm.addValidator(FormController.Validators.matches(password, "As passwords não coincidem"));

        form.setValue("email", "mau");
        assertThat(email.dirty.get()).isTrue();
        assertThat(email.error.get()).isEqualTo("Email inválido");
        assertThat(email.hasError.get()).isTrue();

        form.setValue("password", "segredo99");
        form.setValue("confirm", "diferente");
        assertThat(confirm.error.get()).isEqualTo("As passwords não coincidem");

        AtomicBoolean submitted = new AtomicBoolean(false);
        assertThat(form.submit(() -> submitted.set(true))).isFalse();
        assertThat(submitted.get()).isFalse();

        form.setValue("email", "dev@kairo.dev");
        form.setValue("confirm", "segredo99");
        assertThat(form.submit(() -> submitted.set(true))).isTrue();
        assertThat(submitted.get()).isTrue();

        var snapshot = form.safeSnapshot();
        assertThat(snapshot).containsEntry("email", "dev@kairo.dev");
        assertThat(snapshot).containsEntry("password", "••••••");
        assertThat(snapshot).containsEntry("confirm", "••••••");

        form.reset();
        assertThat(email.value.get()).isEmpty();
        assertThat(email.dirty.get()).isFalse();
        assertThat(email.touched.get()).isFalse();
    }
}
