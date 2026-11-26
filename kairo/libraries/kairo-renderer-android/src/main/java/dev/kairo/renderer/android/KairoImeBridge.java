package dev.kairo.renderer.android;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import dev.kairo.core.KairoId;
import dev.kairo.events.CommonEvents;
import dev.kairo.events.UIEvent;
import dev.kairo.state.TwoWayLoopGuard;

import java.util.function.Consumer;

/**
 * Manages EditText IME composition preservation, two-way loop protection,
 * cursor safety, input types, maxLength, focus and submit actions.
 */
public final class KairoImeBridge {
    private final TwoWayLoopGuard guard = new TwoWayLoopGuard();

    public void attachEditTextListeners(
            KairoId id,
            EditText editText,
            boolean multiLine,
            Consumer<UIEvent> eventSink) {
        if (multiLine) {
            editText.setSingleLine(false);
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        } else {
            editText.setSingleLine(true);
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        }

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                guard.runFromView(() -> eventSink.accept(new CommonEvents.InputEvent(
                        id,
                        s.toString(),
                        Math.max(0, editText.getSelectionStart()),
                        Math.max(0, editText.getSelectionEnd())
                )));
            }
        });

        editText.setOnFocusChangeListener((v, hasFocus) ->
                eventSink.accept(new CommonEvents.FocusEvent(id, hasFocus))
        );

        editText.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSubmitKey = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSubmitKey && !multiLine) {
                eventSink.accept(new CommonEvents.SubmitEvent(id));
                return true;
            }
            return false;
        });
    }

    public void applyModelText(EditText editText, String nextValue) {
        String safeVal = nextValue == null ? "" : nextValue;
        guard.runFromModel(() -> {
            Editable currentEditable = editText.getText();
            String current = currentEditable != null ? currentEditable.toString() : "";
            if (current.equals(safeVal)) {
                return;
            }
            // Do not clobber active IME composing spans unless model diverged cleanly.
            if (currentEditable != null && BaseInputConnection.getComposingSpanStart(currentEditable) != -1
                    && safeVal.equals(current)) {
                return;
            }
            int selStart = Math.max(0, Math.min(editText.getSelectionStart(), safeVal.length()));
            editText.setText(safeVal);
            editText.setSelection(selStart);
        });
    }

    public void applyInputType(EditText editText, String type) {
        if (type == null) return;
        switch (type.toLowerCase()) {
            case "password" -> editText.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            case "email" -> editText.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            case "number" -> editText.setInputType(
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            case "phone" -> editText.setInputType(InputType.TYPE_CLASS_PHONE);
            default -> editText.setInputType(InputType.TYPE_CLASS_TEXT);
        }
    }

    public void applyMaxLength(EditText editText, String rawMaxLength) {
        if (rawMaxLength == null || rawMaxLength.isBlank()) {
            editText.setFilters(new InputFilter[0]);
            return;
        }
        try {
            int max = Integer.parseInt(rawMaxLength.trim());
            if (max > 0) {
                editText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
            }
        } catch (NumberFormatException ignored) {
            // Keep existing filters when malformed attribute is supplied
        }
    }

    public void showKeyboard(View target) {
        if (target == null) return;
        target.requestFocus();
        InputMethodManager imm = (InputMethodManager) target.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public void hideKeyboard(View target) {
        if (target == null) return;
        InputMethodManager imm = (InputMethodManager) target.getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && target.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }
}
