package dev.kairo.components.input;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class InputCatalogTest {
    @Test
    void registersAllFiveInputControlsAndParsesSelectOptions() {
        ComponentRegistry registry = new ComponentRegistry();
        InputComponents.registerAll(registry);

        assertThat(registry.all().keySet())
                .containsExactlyInAnyOrder("Input", "TextArea", "Checkbox", "Switch", "Select");

        for (String tag : List.of("Input", "TextArea", "Checkbox", "Switch", "Select")) {
            ComponentDescriptor desc = registry.find(tag).orElseThrow();
            assertThat(desc.accessibility().focusable()).isTrue();
            assertThat(desc.supportsEvent("onFocus")).isTrue();
        }

        List<SelectOption> opts = List.of(
                new SelectOption("pt", "Português"),
                new SelectOption("en", "English"),
                new SelectOption("jp", "日本語", true)
        );
        String serialized = SelectOption.serialize(opts);
        List<SelectOption> parsed = SelectOption.parse(serialized);
        assertThat(parsed).containsExactlyElementsOf(opts);
    }
}
