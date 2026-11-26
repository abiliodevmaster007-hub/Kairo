package dev.kairo.components.basic;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.ir.NodeIR;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

final class BasicCatalogTest {
    private static final List<String> REQUIRED_V1_BASIC_COMPONENTS = List.of(
            "Screen", "Column", "Row", "Box", "Stack", "Scroll",
            "Text", "Button", "Image", "Card", "Divider", "Progress"
    );

    @Test
    void registersAllTwelveV1BasicComponentsWithExpectedContracts() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.registerModule(BasicComponents::registerAll);

        assertThat(registry.all().keySet()).containsAll(REQUIRED_V1_BASIC_COMPONENTS);

        ComponentDescriptor button = registry.find("Button").orElseThrow();
        assertThat(button.accessibility().focusable()).isTrue();
        assertThat(button.supportsEvent("onClick")).isTrue();
        assertThat(button.childrenPolicy()).isEqualTo(ComponentDescriptor.ChildrenPolicy.NONE);

        ComponentDescriptor image = registry.find("Image").orElseThrow();
        assertThat(image.properties().stream().filter(ComponentDescriptor.PropertyDescriptor::required)
                .map(ComponentDescriptor.PropertyDescriptor::name))
                .containsExactlyInAnyOrder("src", "alt");
        assertThat(image.defaultProperties()).containsEntry("fit", "contain");

        ComponentDescriptor progress = registry.find("Progress").orElseThrow();
        assertThat(progress.accessibility().liveRegion()).isTrue();

        ComponentDescriptor scroll = registry.find("Scroll").orElseThrow();
        assertThat(scroll.childrenPolicy()).isEqualTo(ComponentDescriptor.ChildrenPolicy.SINGLE);
    }

    @Test
    void fluentBasicUIDslBuildsPlatformNeutralNodeIRTree() {
        NodeIR tree = BasicUI.screen("profile")
                .child(BasicUI.column().prop("padding", "16").prop("gap", "12")
                        .child(BasicUI.card()
                                .child(BasicUI.row().prop("gap", "8")
                                        .child(BasicUI.image("avatar.png", "Foto de perfil"))
                                        .child(BasicUI.text("Abílio").classes("heading"))))
                        .child(BasicUI.divider())
                        .child(BasicUI.button("Guardar").id("save").on("onClick", "saveProfile")))
                .build();

        assertThat(tree.componentType()).isEqualTo("Screen");
        assertThat(tree.logicalId()).isEqualTo("profile");
        assertThat(tree.children().get(0).children()).hasSize(3);
        assertThat(tree.children().get(0).children().get(2).eventHandlers()).containsEntry("onClick", "saveProfile");
    }
}
