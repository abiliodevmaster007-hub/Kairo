package dev.kairo.renderer.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import dev.kairo.components.input.SelectOption;
import dev.kairo.core.KairoId;
import dev.kairo.events.CommonEvents;
import dev.kairo.events.UIEvent;
import dev.kairo.resources.ResourceManager;
import dev.kairo.state.TwoWayLoopGuard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Instantiates native Android widgets for Kairo v1 components and applies component-level properties.
 * Isolated inside kairo-renderer-android.
 */
public final class KairoViewFactory {
    private final Context context;
    private final KairoImeBridge imeBridge;
    private final ResourceManager resourceManager;
    private final TwoWayLoopGuard controlGuard = new TwoWayLoopGuard();
    private final Map<KairoId, List<SelectOption>> selectOptionsByNode = new LinkedHashMap<>();

    public KairoViewFactory(Context context, KairoImeBridge imeBridge, ResourceManager resourceManager) {
        this.context = context;
        this.imeBridge = imeBridge;
        this.resourceManager = resourceManager;
    }

    public View createView(KairoId id, String componentType, Consumer<UIEvent> eventSink) {
        return switch (componentType) {
            case "Screen", "Column", "Row", "Box", "Stack", "Card", "Form", "FormField", "Routes" ->
                    new KairoLayoutHostGroup(context);
            case "Scroll", "List" -> {
                KairoLayoutHostGroup.KairoScrollView scroll = new KairoLayoutHostGroup.KairoScrollView(context);
                scroll.setScrollListener((x, y) -> eventSink.accept(new CommonEvents.ScrollEvent(id, x, y)));
                yield scroll;
            }
            case "Text" -> new TextView(context);
            case "Button" -> {
                Button btn = new Button(context);
                btn.setOnClickListener(v -> eventSink.accept(new CommonEvents.ClickEvent(id)));
                btn.setOnTouchListener((v, motion) -> {
                    int action = motion.getActionMasked();
                    if (action == MotionEvent.ACTION_DOWN) {
                        eventSink.accept(new CommonEvents.PointerPressEvent(id, true));
                    } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                        eventSink.accept(new CommonEvents.PointerPressEvent(id, false));
                    }
                    return false;
                });
                yield btn;
            }
            case "Input" -> {
                EditText et = new EditText(context);
                imeBridge.attachEditTextListeners(id, et, false, eventSink);
                yield et;
            }
            case "TextArea" -> {
                EditText et = new EditText(context);
                imeBridge.attachEditTextListeners(id, et, true, eventSink);
                yield et;
            }
            case "Checkbox" -> {
                CheckBox cb = new CheckBox(context);
                cb.setOnCheckedChangeListener((btn, isChecked) ->
                        controlGuard.runFromView(() -> eventSink.accept(
                                new CommonEvents.ChangeEvent<>(id, !isChecked, isChecked)
                        ))
                );
                cb.setOnFocusChangeListener((v, focused) ->
                        eventSink.accept(new CommonEvents.FocusEvent(id, focused)));
                yield cb;
            }
            case "Switch" -> {
                Switch sw = new Switch(context);
                sw.setOnCheckedChangeListener((btn, isChecked) ->
                        controlGuard.runFromView(() -> eventSink.accept(
                                new CommonEvents.ChangeEvent<>(id, !isChecked, isChecked)
                        ))
                );
                sw.setOnFocusChangeListener((v, focused) ->
                        eventSink.accept(new CommonEvents.FocusEvent(id, focused)));
                yield sw;
            }
            case "Select" -> {
                Spinner spinner = new Spinner(context);
                spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long rowId) {
                        List<SelectOption> opts = selectOptionsByNode.get(id);
                        if (opts == null || position < 0 || position >= opts.size()) return;
                        SelectOption chosen = opts.get(position);
                        if (chosen.disabled()) return;
                        controlGuard.runFromView(() -> eventSink.accept(
                                new CommonEvents.ChangeEvent<>(id, null, chosen.value())
                        ));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
                yield spinner;
            }
            case "Image" -> {
                ImageView iv = new ImageView(context);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                yield iv;
            }
            case "Divider" -> new View(context);
            case "Progress" -> new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
            default -> new FrameLayout(context);
        };
    }

    public void applyProperty(KairoId id, View view, String componentType, String property, String value) {
        if (view == null || property == null) return;
        String safeVal = value == null ? "" : value;

        KairoAccessibilityBridge.applyProperty(view, property, value);

        if ("text".equals(property) || "value".equals(property)) {
            if (view instanceof EditText et) {
                imeBridge.applyModelText(et, safeVal);
            } else if (view instanceof Spinner sp && "value".equals(property)) {
                applySelectedOption(id, sp, safeVal);
            } else if (view instanceof ProgressBar pb && "value".equals(property)) {
                try {
                    pb.setProgress((int) Math.round(Double.parseDouble(safeVal)));
                } catch (NumberFormatException ignored) {}
            } else if (view instanceof TextView tv) {
                tv.setText(safeVal);
            }
        } else if ("label".equals(property) && view instanceof CompoundButton cb) {
            cb.setText(safeVal);
        } else if ("placeholder".equals(property) || "hint".equals(property)) {
            if (view instanceof EditText et) {
                et.setHint(value);
            }
        } else if ("type".equals(property) && view instanceof EditText et) {
            imeBridge.applyInputType(et, safeVal);
        } else if ("maxLength".equals(property) && view instanceof EditText et) {
            imeBridge.applyMaxLength(et, value);
        } else if ("rows".equals(property) && view instanceof EditText et) {
            try {
                int rows = Integer.parseInt(safeVal.trim());
                if (rows > 0) et.setMinLines(rows);
            } catch (NumberFormatException ignored) {}
        } else if ("checked".equals(property) && view instanceof CompoundButton cb) {
            boolean checked = "true".equalsIgnoreCase(safeVal);
            controlGuard.runFromModel(() -> {
                if (cb.isChecked() != checked) {
                    cb.setChecked(checked);
                }
            });
        } else if ("options".equals(property) && view instanceof Spinner sp) {
            applySelectOptions(id, sp, safeVal);
        } else if ("src".equals(property) && view instanceof ImageView iv) {
            applyLocalImage(iv, safeVal);
        } else if ("scaleType".equals(property) && view instanceof ImageView iv) {
            if ("contain".equalsIgnoreCase(safeVal) || "fit".equalsIgnoreCase(safeVal)) {
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            } else {
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            }
        } else if ("max".equals(property) && view instanceof ProgressBar pb) {
            try {
                pb.setMax(Math.max(1, (int) Math.round(Double.parseDouble(safeVal))));
            } catch (NumberFormatException ignored) {}
        } else if ("indeterminate".equals(property) && view instanceof ProgressBar pb) {
            pb.setIndeterminate("true".equalsIgnoreCase(safeVal));
        } else if ("disabled".equals(property)) {
            view.setEnabled(!"true".equalsIgnoreCase(safeVal));
        } else if ("visible".equals(property)) {
            view.setVisibility("false".equalsIgnoreCase(safeVal) ? View.GONE : View.VISIBLE);
        }
    }

    public void cleanupNode(KairoId id) {
        selectOptionsByNode.remove(id);
    }

    private void applySelectOptions(KairoId id, Spinner spinner, String serialized) {
        List<SelectOption> options = SelectOption.parseList(serialized);
        selectOptionsByNode.put(id, options);
        List<String> labels = new ArrayList<>(options.size());
        for (SelectOption opt : options) {
            labels.add(opt.label());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                labels
        );
        controlGuard.runFromModel(() -> spinner.setAdapter(adapter));
    }

    private void applySelectedOption(KairoId id, Spinner spinner, String selectedValue) {
        List<SelectOption> options = selectOptionsByNode.get(id);
        if (options == null) return;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).value().equals(selectedValue)) {
                final int index = i;
                controlGuard.runFromModel(() -> {
                    if (spinner.getSelectedItemPosition() != index) {
                        spinner.setSelection(index);
                    }
                });
                return;
            }
        }
    }

    private void applyLocalImage(ImageView imageView, String rawPath) {
        if (resourceManager == null || rawPath.isBlank()) {
            return;
        }
        try {
            Optional<ResourceManager.LocalResource> resOpt = resourceManager.resolve(rawPath);
            if (resOpt.isPresent()) {
                byte[] bytes = resOpt.get().copyBytes();
                Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp != null) {
                    imageView.setImageBitmap(bmp);
                }
            }
        } catch (RuntimeException ignored) {
            // Forbidden or missing local assets fail cleanly without crashing the UI frame
        }
    }
}
