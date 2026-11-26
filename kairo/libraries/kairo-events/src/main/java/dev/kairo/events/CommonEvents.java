package dev.kairo.events;

import dev.kairo.core.KairoId;

public final class CommonEvents {
    private CommonEvents() {}

    public static final class ClickEvent extends UIEvent {
        public ClickEvent(KairoId target) { super(target); }
    }

    public static final class InputEvent extends UIEvent {
        private final String value;
        private final int selectionStart;
        private final int selectionEnd;

        public InputEvent(KairoId target, String value, int selectionStart, int selectionEnd) {
            super(target);
            this.value = value;
            this.selectionStart = selectionStart;
            this.selectionEnd = selectionEnd;
        }

        public String value() { return value; }
        public int selectionStart() { return selectionStart; }
        public int selectionEnd() { return selectionEnd; }
    }

    public static final class ChangeEvent<T> extends UIEvent {
        private final T previousValue;
        private final T newValue;

        public ChangeEvent(KairoId target, T previousValue, T newValue) {
            super(target);
            this.previousValue = previousValue;
            this.newValue = newValue;
        }

        public T previousValue() { return previousValue; }
        public T newValue() { return newValue; }
    }

    public static final class FocusEvent extends UIEvent {
        private final boolean focused;

        public FocusEvent(KairoId target, boolean focused) {
            super(target);
            this.focused = focused;
        }

        public boolean focused() { return focused; }
    }

    public static final class PointerPressEvent extends UIEvent {
        private final boolean pressed;

        public PointerPressEvent(KairoId target, boolean pressed) {
            super(target);
            this.pressed = pressed;
        }

        public boolean pressed() { return pressed; }
    }

    public static final class HoverEvent extends UIEvent {
        private final boolean hovered;

        public HoverEvent(KairoId target, boolean hovered) {
            super(target);
            this.hovered = hovered;
        }

        public boolean hovered() { return hovered; }
    }

    public static final class KeyEvent extends UIEvent {
        private final String key;
        private final boolean shiftPressed;
        private final boolean ctrlPressed;

        public KeyEvent(KairoId target, String key, boolean shiftPressed, boolean ctrlPressed) {
            super(target);
            this.key = key;
            this.shiftPressed = shiftPressed;
            this.ctrlPressed = ctrlPressed;
        }

        public String key() { return key; }
        public boolean shiftPressed() { return shiftPressed; }
        public boolean ctrlPressed() { return ctrlPressed; }
    }

    public static final class ScrollEvent extends UIEvent {
        private final double offsetX;
        private final double offsetY;

        public ScrollEvent(KairoId target, double offsetX, double offsetY) {
            super(target);
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }

        public double offsetX() { return offsetX; }
        public double offsetY() { return offsetY; }
    }

    public static final class SubmitEvent extends UIEvent {
        public SubmitEvent(KairoId target) { super(target); }
    }
}
