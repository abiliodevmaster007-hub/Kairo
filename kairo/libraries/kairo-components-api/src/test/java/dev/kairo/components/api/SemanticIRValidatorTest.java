package dev.kairo.components.api;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.NodeIR;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class SemanticIRValidatorTest {
    private ComponentRegistry baseRegistry() {
        ComponentRegistry registry = new ComponentRegistry();
        registry.register(new ComponentDescriptor(
                "Screen",
                ComponentDescriptor.SemanticRole.CONTAINER,
                ComponentDescriptor.LayoutAxisHint.VERTICAL,
                ComponentDescriptor.ChildrenPolicy.MULTIPLE,
                List.of(), List.of(), List.of()
        ));
        registry.register(new ComponentDescriptor(
                "Button",
                ComponentDescriptor.SemanticRole.BUTTON,
                ComponentDescriptor.LayoutAxisHint.LEAF,
                ComponentDescriptor.ChildrenPolicy.NONE,
                List.of(new ComponentDescriptor.PropertyDescriptor("text", String.class, false, "")),
                List.of(new ComponentDescriptor.EventDescriptor("onClick", Object.class)),
                List.of()
        ));
        registry.register(new ComponentDescriptor(
                "Image",
                ComponentDescriptor.SemanticRole.IMAGE,
                ComponentDescriptor.LayoutAxisHint.LEAF,
                ComponentDescriptor.ChildrenPolicy.NONE,
                List.of(
                        new ComponentDescriptor.PropertyDescriptor("src", String.class, true, null),
                        new ComponentDescriptor.PropertyDescriptor("alt", String.class, true, null)
                ),
                List.of(),
                List.of()
        ));
        return registry;
    }

    @Test
    void detectsUnknownComponentMissingRequiredPropAndForbiddenChildren() {
        NodeIR badImage = new NodeIR("Image", "logo", null, List.of(),
                Map.of("alt", "Logo"), Map.of(), Map.of(), null, List.of(), new SourceLocation("home.xml", 3, 5));
        NodeIR childInsideButton = new NodeIR("UnknownWidget", null, null, List.of(),
                Map.of(), Map.of(), Map.of(), null, List.of(), new SourceLocation("home.xml", 5, 7));
        NodeIR badButton = new NodeIR("Button", "btn", null, List.of(),
                Map.of("text", "Ok"), Map.of(), Map.of("onHover", "hoverFn"), null,
                List.of(childInsideButton), new SourceLocation("home.xml", 4, 5));
        NodeIR root = new NodeIR("Screen", "home", null, List.of(),
                Map.of(), Map.of(), Map.of(), null, List.of(badImage, badButton), new SourceLocation("home.xml", 1, 1));

        IRDocument doc = new IRDocument("home.xml", root, List.of(), Map.of(), new Diagnostic.Bag());
        Diagnostic.Bag result = new SemanticIRValidator().validate(doc, baseRegistry());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.all())
                .extracting(Diagnostic::code)
                .contains("SEM_MISSING_REQUIRED_PROP", "SEM_UNSUPPORTED_EVENT", "SEM_FORBIDDEN_CHILDREN", "SEM_UNKNOWN_COMPONENT");
    }
}
