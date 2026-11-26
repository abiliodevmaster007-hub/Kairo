package dev.kairo.ir;

import dev.kairo.core.Diagnostic;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates structural invariants on NodeIR (duplicate IDs, missing keys on List, binding grammar, slots).
 */
public final class IRValidator {
    private final BindingExpressionValidator expressionValidator = new BindingExpressionValidator();

    public Diagnostic.Bag validate(NodeIR root) {
        Diagnostic.Bag bag = new Diagnostic.Bag();
        validateInto(root, bag);
        return bag;
    }

    public void validateInto(NodeIR root, Diagnostic.Bag bag) {
        Set<String> seenIds = new HashSet<>();
        validateNode(root, seenIds, bag);
    }

    private void validateNode(NodeIR node, Set<String> seenIds, Diagnostic.Bag bag) {
        if (node.componentType() == null || node.componentType().isBlank()) {
            bag.error("IR_EMPTY_TYPE", "Component type cannot be blank", node.sourceLocation());
        }
        if (node.logicalId() != null && !node.logicalId().isBlank()) {
            if (!seenIds.add(node.logicalId())) {
                bag.error("IR_DUPLICATE_ID", "Duplicate logical id '" + node.logicalId() + "'", node.sourceLocation());
            }
        }
        if ("List".equals(node.componentType()) && (node.stableKeyExpr() == null || node.stableKeyExpr().isBlank())) {
            bag.error("IR_LIST_MISSING_KEY", "<List> requires a stable key attribute", node.sourceLocation());
        }
        if ("slot".equals(node.componentType()) && (node.slotName() == null || node.slotName().isBlank())) {
            bag.error("IR_SLOT_MISSING_NAME", "<slot> requires a non-blank 'name' attribute", node.sourceLocation());
        }
        for (BindingSpec binding : node.bindings().values()) {
            expressionValidator.validate(binding.expression(), node.sourceLocation(), bag);
        }
        for (NodeIR child : node.children()) {
            validateNode(child, seenIds, bag);
        }
    }
}
