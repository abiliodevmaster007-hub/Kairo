package dev.kairo.testkit;

import dev.kairo.events.CommonEvents;

/**
 * Test helper for dispatching user interactions against FakeRenderer by logical ID.
 */
public final class EventTestDriver {
    private final FakeRenderer renderer;

    public EventTestDriver(FakeRenderer renderer) {
        this.renderer = renderer;
    }

    public void click(String logicalId) {
        FakeRenderer.FakeNode node = requireNode(logicalId);
        renderer.dispatch(new CommonEvents.ClickEvent(node.id()));
    }

    public void typeText(String logicalId, String value) {
        FakeRenderer.FakeNode node = requireNode(logicalId);
        int len = value != null ? value.length() : 0;
        renderer.dispatch(new CommonEvents.InputEvent(node.id(), value, len, len));
    }

    public void focus(String logicalId, boolean focused) {
        FakeRenderer.FakeNode node = requireNode(logicalId);
        renderer.dispatch(new CommonEvents.FocusEvent(node.id(), focused));
    }

    public void submit(String logicalId) {
        FakeRenderer.FakeNode node = requireNode(logicalId);
        renderer.dispatch(new CommonEvents.SubmitEvent(node.id()));
    }

    private FakeRenderer.FakeNode requireNode(String logicalId) {
        FakeRenderer.FakeNode node = renderer.findByLogicalId(logicalId);
        if (node == null) {
            throw new IllegalArgumentException("No mounted FakeNode found with logicalId: " + logicalId);
        }
        return node;
    }
}
