package dev.kairo.components.form;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentDescriptor.ChildrenPolicy;
import dev.kairo.components.api.ComponentDescriptor.LayoutAxisHint;
import dev.kairo.components.api.ComponentDescriptor.SemanticRole;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.events.CommonEvents;
import dev.kairo.state.Derived;
import dev.kairo.state.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Platform-independent Form controller with sync validators, validateOn policies (SUBMIT/BLUR/CHANGE),
 * touched/dirty tracking, reactive validity, and secret redaction for sensitive fields.
 */
public final class FormController {
    public enum ValidateOn {
        SUBMIT,
        BLUR,
        CHANGE
    }

    @FunctionalInterface
    public interface Validator {
        String validate(String value);
    }

    public static final class Validators {
        private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

        private Validators() {}

        public static Validator required(String message) {
            return val -> (val == null || val.isBlank()) ? message : null;
        }

        public static Validator minLength(int min, String message) {
            return val -> (val == null || val.length() < min) ? message : null;
        }

        public static Validator maxLength(int max, String message) {
            return val -> (val != null && val.length() > max) ? message : null;
        }

        public static Validator email(String message) {
            return val -> (val == null || !EMAIL_PATTERN.matcher(val.trim()).matches()) ? message : null;
        }

        public static Validator pattern(Pattern regex, String message) {
            return val -> (val == null || !regex.matcher(val).matches()) ? message : null;
        }

        public static Validator matches(FieldState other, String message) {
            return val -> !Objects.equals(val, other.value.get()) ? message : null;
        }

        public static Validator compose(Validator... validators) {
            return val -> {
                for (Validator v : validators) {
                    String err = v.validate(val);
                    if (err != null) return err;
                }
                return null;
            };
        }
    }

    public static final class FieldState {
        public final State<String> value = State.of("");
        public final State<Boolean> touched = State.of(false);
        public final State<Boolean> dirty = State.of(false);
        public final State<String> error = State.of(null);
        public final Derived<Boolean> hasError = Derived.derive(() -> error.get() != null && !error.get().isBlank());
        private final List<Validator> validators = new ArrayList<>();
        private boolean sensitive;

        public FieldState sensitive(boolean sensitive) {
            this.sensitive = sensitive;
            return this;
        }

        public boolean isSensitive() {
            return sensitive;
        }

        public void addValidator(Validator validator) {
            validators.add(validator);
        }

        public boolean validate() {
            String current = value.get();
            for (Validator v : validators) {
                String msg = v.validate(current);
                if (msg != null) {
                    error.set(msg);
                    return false;
                }
            }
            error.set(null);
            return true;
        }
    }

    private final ValidateOn validateOn;
    private final Map<String, FieldState> fields = new LinkedHashMap<>();
    public final State<Boolean> submitting = State.of(false);

    public FormController() {
        this(ValidateOn.SUBMIT);
    }

    public FormController(ValidateOn validateOn) {
        this.validateOn = validateOn != null ? validateOn : ValidateOn.SUBMIT;
    }

    public FieldState field(String name) {
        return fields.computeIfAbsent(name, ignored -> new FieldState());
    }

    public void setValue(String fieldName, String nextValue) {
        FieldState f = field(fieldName);
        State.transaction(() -> {
            f.value.set(nextValue != null ? nextValue : "");
            f.dirty.set(true);
            if (validateOn == ValidateOn.CHANGE) {
                f.validate();
            }
        });
    }

    public void blur(String fieldName) {
        FieldState f = field(fieldName);
        State.transaction(() -> {
            f.touched.set(true);
            if (validateOn == ValidateOn.BLUR || validateOn == ValidateOn.CHANGE) {
                f.validate();
            }
        });
    }

    public boolean validateAll() {
        boolean[] allValid = { true };
        State.transaction(() -> {
            for (FieldState field : fields.values()) {
                field.touched.set(true);
                if (!field.validate()) {
                    allValid[0] = false;
                }
            }
        });
        return allValid[0];
    }

    public boolean submit(Runnable onValid) {
        if (!validateAll()) {
            return false;
        }
        submitting.set(true);
        try {
            if (onValid != null) {
                onValid.run();
            }
            return true;
        } finally {
            submitting.set(false);
        }
    }

    public void reset() {
        State.transaction(() -> {
            for (FieldState field : fields.values()) {
                field.value.set("");
                field.touched.set(false);
                field.dirty.set(false);
                field.error.set(null);
            }
            submitting.set(false);
        });
    }

    public Map<String, String> safeSnapshot() {
        Map<String, String> out = new LinkedHashMap<>();
        for (var entry : fields.entrySet()) {
            FieldState f = entry.getValue();
            out.put(entry.getKey(), f.isSensitive() ? "••••••" : f.value.get());
        }
        return Collections.unmodifiableMap(out);
    }

    public Map<String, State<String>> twoWayStringStates() {
        Map<String, State<String>> map = new LinkedHashMap<>();
        fields.forEach((name, f) -> map.put(name, f.value));
        return Collections.unmodifiableMap(map);
    }

    public static void registerAll(ComponentRegistry registry) {
        registry.register(ComponentDescriptor.builder("Form")
                .role(SemanticRole.FORM)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("validateOn", String.class, "submit")
                .prop("disabled", Boolean.class, "false")
                .event("onSubmit", CommonEvents.SubmitEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("FormField")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.SINGLE)
                .requiredProp("name", String.class)
                .prop("label", String.class, "")
                .prop("helperText", String.class, "")
                .prop("sensitive", Boolean.class, "false")
                .build());
    }
}
