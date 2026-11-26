package dev.kairo.components.input;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentDescriptor.ChildrenPolicy;
import dev.kairo.components.api.ComponentDescriptor.LayoutAxisHint;
import dev.kairo.components.api.ComponentDescriptor.SemanticRole;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.events.CommonEvents;

/**
 * Platform-independent input controls: Input, TextArea, Checkbox, Switch, Select.
 */
public final class InputComponents {
    private InputComponents() {}

    public static void registerAll(ComponentRegistry registry) {
        registry.register(ComponentDescriptor.builder("Input")
                .role(SemanticRole.INPUT)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("value", String.class, "")
                .prop("label", String.class, "")
                .prop("placeholder", String.class, "")
                .prop("type", String.class, "text")
                .prop("maxLength", Integer.class)
                .prop("disabled", Boolean.class, "false")
                .prop("error", Boolean.class, "false")
                .event("onInput", CommonEvents.InputEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .event("onSubmit", CommonEvents.SubmitEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("TextArea")
                .role(SemanticRole.INPUT)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("value", String.class, "")
                .prop("label", String.class, "")
                .prop("placeholder", String.class, "")
                .prop("rows", Integer.class, "4")
                .prop("maxLength", Integer.class)
                .prop("disabled", Boolean.class, "false")
                .prop("error", Boolean.class, "false")
                .event("onInput", CommonEvents.InputEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Checkbox")
                .role(SemanticRole.CHECKBOX)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("checked", Boolean.class, "false")
                .prop("label", String.class, "")
                .prop("disabled", Boolean.class, "false")
                .prop("error", Boolean.class, "false")
                .event("onChange", CommonEvents.ChangeEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Switch")
                .role(SemanticRole.SWITCH)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("checked", Boolean.class, "false")
                .prop("label", String.class, "")
                .prop("disabled", Boolean.class, "false")
                .event("onChange", CommonEvents.ChangeEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Select")
                .role(SemanticRole.SELECT)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("value", String.class, "")
                .prop("options", String.class, "")
                .prop("placeholder", String.class, "")
                .prop("disabled", Boolean.class, "false")
                .prop("error", Boolean.class, "false")
                .event("onChange", CommonEvents.ChangeEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());
    }
}
