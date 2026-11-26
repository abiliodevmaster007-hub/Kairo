package dev.kairo.renderer.android;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import dev.kairo.style.ComputedStyle;

import java.util.Locale;

/**
 * Applies KairoSceneEncoder snapshots onto Android Views using GradientDrawable surfaces,
 * opacity, visibility, clipping, and typography without leaking Android types upstream.
 */
public final class KairoStyleApplier {
    private KairoStyleApplier() {}

    public static void apply(View view, ComputedStyle style, KairoSceneEncoder.EncodedVisualNode encoded) {
        if (view == null || encoded == null) return;

        view.setVisibility(encoded.visible() ? View.VISIBLE : View.GONE);
        view.setAlpha(encoded.opacity());
        view.setEnabled((encoded.visualFlags() & KairoSceneEncoder.FLAG_DISABLED) == 0);

        if (view instanceof ViewGroup vg) {
            vg.setClipChildren(encoded.clipChildren());
            vg.setClipToPadding(encoded.clipChildren());
        }

        boolean hasBg = KairoColorParser.hasVisibleAlpha(encoded.backgroundArgb());
        boolean hasBorder = encoded.borderWidthPx() > 0.0f && KairoColorParser.hasVisibleAlpha(encoded.borderArgb());
        if (hasBg || hasBorder || encoded.cornerRadiusPx() > 0.0f) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            drawable.setColor(hasBg ? encoded.backgroundArgb() : 0x00000000);
            if (encoded.cornerRadiusPx() > 0.0f) {
                drawable.setCornerRadius(encoded.cornerRadiusPx());
            }
            if (hasBorder) {
                drawable.setStroke(Math.max(1, Math.round(encoded.borderWidthPx())), encoded.borderArgb());
            }
            view.setBackground(drawable);
        } else if (style != null) {
            view.setBackground(null);
        }

        if (style != null && !(view instanceof ViewGroup)) {
            ComputedStyle.EdgeInsets pad = style.getInsets("padding");
            view.setPadding(
                    Math.max(0, (int) Math.round(pad.left())),
                    Math.max(0, (int) Math.round(pad.top())),
                    Math.max(0, (int) Math.round(pad.right())),
                    Math.max(0, (int) Math.round(pad.bottom()))
            );
        }

        if (view instanceof TextView tv) {
            if (encoded.fontSizePx() > 0.0f) {
                tv.setTextSize(encoded.fontSizePx());
            }
            tv.setTextColor(encoded.textArgb());
            tv.setTypeface(encoded.bold() ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

            if (encoded.textAlign() != null) {
                switch (encoded.textAlign().toLowerCase(Locale.ROOT)) {
                    case "center" -> tv.setGravity(Gravity.CENTER_HORIZONTAL);
                    case "right", "end" -> tv.setGravity(Gravity.RIGHT);
                    default -> tv.setGravity(Gravity.LEFT);
                }
            }

            if (style != null) {
                int maxLines = (int) style.getDouble("max-lines", -1);
                if (maxLines > 0) {
                    tv.setMaxLines(maxLines);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                }
            }
        }
    }
}
