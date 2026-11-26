package dev.kairo.components.basic;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentDescriptor.ChildrenPolicy;
import dev.kairo.components.api.ComponentDescriptor.LayoutAxisHint;
import dev.kairo.components.api.ComponentDescriptor.SemanticRole;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.events.CommonEvents;

/**
 * Platform-independent v1 base catalog of 12 components:
 * Screen, Column, Row, Box, Stack, Scroll, Text, Button, Image, Card, Divider, Progress.
 */
public final class BasicComponents {
    private BasicComponents() {}

    public static void registerAll(ComponentRegistry registry) {
        registry.register(ComponentDescriptor.builder("Screen")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("title", String.class, "")
                .prop("padding", String.class, "0")
                .prop("gap", String.class, "0")
                .build());

        registry.register(ComponentDescriptor.builder("Column")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("gap", String.class, "0")
                .prop("padding", String.class, "0")
                .prop("align", String.class, "stretch")
                .prop("wrap", String.class, "nowrap")
                .build());

        registry.register(ComponentDescriptor.builder("Row")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.HORIZONTAL)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("gap", String.class, "0")
                .prop("padding", String.class, "0")
                .prop("align", String.class, "center")
                .prop("wrap", String.class, "nowrap")
                .build());

        registry.register(ComponentDescriptor.builder("Box")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.OVERLAY)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("padding", String.class, "0")
                .prop("align", String.class, "start")
                .event("onClick", CommonEvents.ClickEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Stack")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.OVERLAY)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("padding", String.class, "0")
                .prop("align", String.class, "center")
                .build());

        registry.register(ComponentDescriptor.builder("Scroll")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.SINGLE)
                .prop("axis", String.class, "vertical")
                .prop("padding", String.class, "0")
                .prop("showsIndicator", Boolean.class, "true")
                .event("onScroll", CommonEvents.ScrollEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Text")
                .role(SemanticRole.TEXT)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("value", String.class, "")
                .prop("text", String.class, "")
                .prop("maxLines", Integer.class)
                .prop("selectable", Boolean.class, "false")
                .prop("heading", Boolean.class, "false")
                .build());

        registry.register(ComponentDescriptor.builder("Button")
                .role(SemanticRole.BUTTON)
                .accessibility(true, false)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("text", String.class, "")
                .prop("disabled", Boolean.class, "false")
                .prop("variant", String.class, "primary")
                .event("onClick", CommonEvents.ClickEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Image")
                .role(SemanticRole.IMAGE)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .requiredProp("src", String.class)
                .requiredProp("alt", String.class)
                .prop("fit", String.class, "contain")
                .build());

        registry.register(ComponentDescriptor.builder("Card")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.VERTICAL)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("padding", String.class, "16")
                .prop("elevation", String.class, "2")
                .prop("variant", String.class, "elevated")
                .prop("disabled", Boolean.class, "false")
                .event("onClick", CommonEvents.ClickEvent.class)
                .event("onFocus", CommonEvents.FocusEvent.class)
                .build());

        registry.register(ComponentDescriptor.builder("Divider")
                .role(SemanticRole.CONTAINER)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("orientation", String.class, "horizontal")
                .prop("thickness", String.class, "1")
                .build());

        registry.register(ComponentDescriptor.builder("Progress")
                .role(SemanticRole.PROGRESS)
                .accessibility(false, true)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .prop("value", Double.class)
                .prop("max", Double.class, "1.0")
                .prop("indeterminate", Boolean.class, "true")
                .build());
    }
}
