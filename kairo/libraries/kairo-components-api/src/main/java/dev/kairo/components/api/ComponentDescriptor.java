package dev.kairo.components.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Neutral metadata describing a Kairo component's props, events, children policy, slots, and accessibility semantics.
 */
public final class ComponentDescriptor {
    public enum ChildrenPolicy {
        NONE, SINGLE, MULTIPLE, TEMPLATE, SLOTS
    }

    public enum SemanticRole {
        CONTAINER, HEADING, TEXT, BUTTON, INPUT, CHECKBOX, SWITCH, SELECT, IMAGE, PROGRESS, LIST, FORM, NAVIGATION
    }

    public enum LayoutAxisHint { VERTICAL, HORIZONTAL, OVERLAY, LEAF }

    public record AccessibilitySpec(SemanticRole role, boolean focusable, boolean liveRegion) {
        public static AccessibilitySpec defaultFor(SemanticRole role) {
            boolean focusable = switch (role) {
                case BUTTON, INPUT, CHECKBOX, SWITCH, SELECT, NAVIGATION -> true;
                default -> false;
            };
            boolean live = role == SemanticRole.PROGRESS;
            return new AccessibilitySpec(role, focusable, live);
        }
    }

    public record PropertyDescriptor(String name, Class<?> type, boolean required, String defaultValue) {}
    public record EventDescriptor(String name, Class<?> eventType) {}

    private final String tag;
    private final SemanticRole semanticRole;
    private final AccessibilitySpec accessibility;
    private final LayoutAxisHint layoutAxis;
    private final ChildrenPolicy childrenPolicy;
    private final List<PropertyDescriptor> properties;
    private final List<EventDescriptor> events;
    private final List<String> slots;

    public ComponentDescriptor(
            String tag,
            SemanticRole semanticRole,
            LayoutAxisHint layoutAxis,
            ChildrenPolicy childrenPolicy,
            List<PropertyDescriptor> properties,
            List<EventDescriptor> events,
            List<String> slots) {
        this(tag, semanticRole, AccessibilitySpec.defaultFor(semanticRole), layoutAxis, childrenPolicy, properties, events, slots);
    }

    public ComponentDescriptor(
            String tag,
            SemanticRole semanticRole,
            AccessibilitySpec accessibility,
            LayoutAxisHint layoutAxis,
            ChildrenPolicy childrenPolicy,
            List<PropertyDescriptor> properties,
            List<EventDescriptor> events,
            List<String> slots) {
        this.tag = tag;
        this.semanticRole = semanticRole;
        this.accessibility = accessibility != null ? accessibility : AccessibilitySpec.defaultFor(semanticRole);
        this.layoutAxis = layoutAxis;
        this.childrenPolicy = childrenPolicy;
        this.properties = List.copyOf(properties);
        this.events = List.copyOf(events);
        this.slots = List.copyOf(slots);
    }

    public static Builder builder(String tag) {
        return new Builder(tag);
    }

    public String tag() { return tag; }
    public SemanticRole semanticRole() { return semanticRole; }
    public AccessibilitySpec accessibility() { return accessibility; }
    public LayoutAxisHint layoutAxis() { return layoutAxis; }
    public ChildrenPolicy childrenPolicy() { return childrenPolicy; }
    public List<PropertyDescriptor> properties() { return properties; }
    public List<EventDescriptor> events() { return events; }
    public List<String> slots() { return slots; }

    public boolean supportsProp(String name) {
        return properties.stream().anyMatch(p -> p.name().equals(name));
    }

    public boolean supportsEvent(String name) {
        return events.stream().anyMatch(e -> e.name().equals(name));
    }

    public Map<String, String> defaultProperties() {
        var defaults = new LinkedHashMap<String, String>();
        for (PropertyDescriptor p : properties) {
            if (p.defaultValue() != null) {
                defaults.put(p.name(), p.defaultValue());
            }
        }
        return defaults;
    }

    public static final class Builder {
        private final String tag;
        private SemanticRole role = SemanticRole.CONTAINER;
        private AccessibilitySpec accessibility;
        private LayoutAxisHint layoutAxis = LayoutAxisHint.VERTICAL;
        private ChildrenPolicy childrenPolicy = ChildrenPolicy.MULTIPLE;
        private final List<PropertyDescriptor> properties = new ArrayList<>();
        private final List<EventDescriptor> events = new ArrayList<>();
        private final List<String> slots = new ArrayList<>();

        private Builder(String tag) {
            this.tag = tag;
        }

        public Builder role(SemanticRole role) {
            this.role = role;
            return this;
        }

        public Builder accessibility(boolean focusable, boolean liveRegion) {
            this.accessibility = new AccessibilitySpec(this.role, focusable, liveRegion);
            return this;
        }

        public Builder axis(LayoutAxisHint layoutAxis) {
            this.layoutAxis = layoutAxis;
            return this;
        }

        public Builder children(ChildrenPolicy childrenPolicy) {
            this.childrenPolicy = childrenPolicy;
            return this;
        }

        public Builder prop(String name, Class<?> type) {
            this.properties.add(new PropertyDescriptor(name, type, false, null));
            return this;
        }

        public Builder prop(String name, Class<?> type, String defaultValue) {
            this.properties.add(new PropertyDescriptor(name, type, false, defaultValue));
            return this;
        }

        public Builder requiredProp(String name, Class<?> type) {
            this.properties.add(new PropertyDescriptor(name, type, true, null));
            return this;
        }

        public Builder event(String name, Class<?> eventType) {
            this.events.add(new EventDescriptor(name, eventType));
            return this;
        }

        public Builder slot(String slotName) {
            this.slots.add(slotName);
            return this;
        }

        public Builder slots(String... slotNames) {
            for (String s : slotNames) {
                this.slots.add(s);
            }
            return this;
        }

        public ComponentDescriptor build() {
            return new ComponentDescriptor(tag, role, accessibility, layoutAxis, childrenPolicy, properties, events, slots);
        }
    }
}
