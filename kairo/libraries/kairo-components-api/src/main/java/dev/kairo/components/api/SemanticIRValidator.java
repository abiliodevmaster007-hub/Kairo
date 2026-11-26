package dev.kairo.components.api;

import dev.kairo.core.Diagnostic;
import dev.kairo.ir.IRDocument;
import dev.kairo.ir.NodeIR;

import java.util.Optional;
import java.util.Set;

/**
 * Validates an IRDocument against explicit ComponentRegistry contracts:
 * component existence, required/known properties, supported events, slots, and children policy.
 */
public final class SemanticIRValidator {
    private static final Set<String> UNIVERSAL_PROPS = Set.of(
            "style", "accessibilityLabel", "visible", "if", "when", "slot", "key"
    );

    public Diagnostic.Bag validate(IRDocument document, ComponentRegistry registry) {
        Diagnostic.Bag bag = new Diagnostic.Bag();
        document.diagnostics().all().forEach(bag::add);
        if (document.root() != null) {
            validateNode(document.root(), document, registry, bag);
        }
        return bag;
    }

    private void validateNode(
            NodeIR node,
            IRDocument document,
            ComponentRegistry registry,
            Diagnostic.Bag bag) {
        String tag = node.componentType();
        if ("slot".equals(tag) || "template".equals(tag)) {
            for (NodeIR child : node.children()) {
                validateNode(child, document, registry, bag);
            }
            return;
        }

        Optional<ComponentDescriptor> descriptorOpt = registry.find(tag);
        if (descriptorOpt.isEmpty()) {
            if (!document.componentImports().containsKey(tag)) {
                bag.error(
                        "SEM_UNKNOWN_COMPONENT",
                        "Unknown component '<" + tag + ">'. Register it in ComponentRegistry or add <import>.",
                        node.sourceLocation()
                );
            }
            for (NodeIR child : node.children()) {
                validateNode(child, document, registry, bag);
            }
            return;
        }

        ComponentDescriptor descriptor = descriptorOpt.get();

        // Check required properties
        for (ComponentDescriptor.PropertyDescriptor prop : descriptor.properties()) {
            boolean hasLiteral = node.properties().containsKey(prop.name());
            boolean hasBinding = node.bindings().containsKey(prop.name());
            if (prop.required() && !hasLiteral && !hasBinding) {
                bag.error(
                        "SEM_MISSING_REQUIRED_PROP",
                        "<" + tag + "> requires property '" + prop.name() + "'",
                        node.sourceLocation()
                );
            }
        }

        // Check unknown properties
        for (String propName : node.properties().keySet()) {
            if (!UNIVERSAL_PROPS.contains(propName) && !descriptor.supportsProp(propName)) {
                bag.warning(
                        "SEM_UNKNOWN_PROP",
                        "Unknown property '" + propName + "' on <" + tag + ">",
                        node.sourceLocation()
                );
            }
        }

        // Check supported events
        for (String eventName : node.eventHandlers().keySet()) {
            if (!descriptor.supportsEvent(eventName)) {
                bag.error(
                        "SEM_UNSUPPORTED_EVENT",
                        "Event '" + eventName + "' is not supported by <" + tag + ">",
                        node.sourceLocation()
                );
            }
        }

        // Validate named slot targets when component declares SLOTS policy
        if (descriptor.childrenPolicy() == ComponentDescriptor.ChildrenPolicy.SLOTS && !descriptor.slots().isEmpty()) {
            for (NodeIR child : node.children()) {
                String slotTarget = child.slotName() != null
                        ? child.slotName()
                        : child.properties().get("slot");
                if (slotTarget != null && !descriptor.slots().contains(slotTarget)) {
                    bag.warning(
                            "SEM_UNKNOWN_SLOT",
                            "Slot '" + slotTarget + "' is not declared on <" + tag + ">",
                            child.sourceLocation()
                    );
                }
            }
        }

        // Check ChildrenPolicy
        switch (descriptor.childrenPolicy()) {
            case NONE -> {
                if (!node.children().isEmpty()) {
                    bag.error(
                            "SEM_FORBIDDEN_CHILDREN",
                            "<" + tag + "> cannot have child elements",
                            node.sourceLocation()
                    );
                }
            }
            case SINGLE -> {
                if (node.children().size() > 1) {
                    bag.error(
                            "SEM_TOO_MANY_CHILDREN",
                            "<" + tag + "> accepts at most 1 child element",
                            node.sourceLocation()
                    );
                }
            }
            case TEMPLATE -> {
                boolean hasTemplate = node.children().stream().anyMatch(c -> "template".equals(c.componentType()));
                if (!hasTemplate) {
                    bag.error(
                            "SEM_MISSING_TEMPLATE",
                            "<" + tag + "> requires a <template> child",
                            node.sourceLocation()
                    );
                }
            }
            case MULTIPLE, SLOTS -> {
                // Any number of children or named slots allowed
            }
        }

        for (NodeIR child : node.children()) {
            validateNode(child, document, registry, bag);
        }
    }
}
