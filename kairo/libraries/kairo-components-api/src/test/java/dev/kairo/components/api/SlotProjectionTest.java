package dev.kairo.components.api;

import dev.kairo.core.Diagnostic;
import dev.kairo.core.SourceLocation;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.NodeIR;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

final class SlotProjectionTest {
    @Test
    void extractsNamedSlotsProjectsIntoTemplateWithFallbackAndValidatesDeclaredSlots() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentDescriptor alertDesc = ComponentDescriptor.builder("AlertBanner")
                .role(ComponentDescriptor.SemanticRole.CONTAINER)
                .children(ComponentDescriptor.ChildrenPolicy.SLOTS)
                .slots("header", "default")
                .prop("variant", String.class, "info")
                .build();
        registry.register(alertDesc);
        registry.register(ComponentDescriptor.builder("Screen").children(ComponentDescriptor.ChildrenPolicy.MULTIPLE).build());
        registry.register(ComponentDescriptor.builder("Text").children(ComponentDescriptor.ChildrenPolicy.NONE).prop("value", String.class).build());

        NodeIR headerNode = new NodeIR("Text", "hdr", null, List.of(), Map.of("value", "Aviso"), Map.of(), Map.of(), "header", List.of(), SourceLocation.UNKNOWN);
        NodeIR badSlotNode = new NodeIR("Text", "extra", null, List.of(), Map.of("value", "X"), Map.of(), Map.of(), "unknownSlot", List.of(), SourceLocation.UNKNOWN);
        NodeIR invocation = new NodeIR("AlertBanner", "alert1", null, List.of(), Map.of(), Map.of(), Map.of(), null, List.of(headerNode, badSlotNode), SourceLocation.UNKNOWN);

        Map<String, NodeIR> extracted = SlotProjection.extractSlots(invocation);
        assertThat(extracted).containsKey("header");

        NodeIR headerPlaceholder = new NodeIR("slot", null, null, List.of(), Map.of("name", "header"), Map.of(), Map.of(), "header", List.of(), SourceLocation.UNKNOWN);
        NodeIR footerFallbackText = new NodeIR("Text", null, null, List.of(), Map.of("value", "Sem rodapé"), Map.of(), Map.of(), null, List.of(), SourceLocation.UNKNOWN);
        NodeIR footerPlaceholder = new NodeIR("slot", null, null, List.of(), Map.of("name", "footer"), Map.of(), Map.of(), "footer", List.of(footerFallbackText), SourceLocation.UNKNOWN);
        NodeIR template = new NodeIR("Column", null, null, List.of(), Map.of(), Map.of(), Map.of(), null, List.of(headerPlaceholder, footerPlaceholder), SourceLocation.UNKNOWN);

        NodeIR projected = SlotProjection.projectSlots(template, extracted);
        assertThat(projected.children()).hasSize(2);
        assertThat(projected.children().get(0).properties()).containsEntry("value", "Aviso");
        assertThat(projected.children().get(1).properties()).containsEntry("value", "Sem rodapé");

        NodeIR root = new NodeIR("Screen", "root", null, List.of(), Map.of(), Map.of(), Map.of(), null, List.of(invocation), SourceLocation.UNKNOWN);
        Diagnostic.Bag bag = new SemanticIRValidator().validate(new IRDocument("test.xml", root, List.of(), Map.of(), new Diagnostic.Bag()), registry);
        assertThat(bag.all()).anyMatch(d -> "SEM_UNKNOWN_SLOT".equals(d.code()));
    }
}
