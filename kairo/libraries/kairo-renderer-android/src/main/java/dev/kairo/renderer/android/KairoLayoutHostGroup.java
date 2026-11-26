package dev.kairo.renderer.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import dev.kairo.layout.LayoutBounds;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Custom ViewGroup & ScrollView host that positions children strictly according to
 * LayoutBounds computed by platform-neutral kairo-layout (never Android XML/Flex re-measure).
 */
public class KairoLayoutHostGroup extends ViewGroup {
    private final Map<View, LayoutBounds> boundsMap = new LinkedHashMap<>();

    public KairoLayoutHostGroup(Context context) {
        super(context);
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setChildBounds(View child, LayoutBounds bounds) {
        if (child == null || bounds == null) return;
        boundsMap.put(child, bounds);
        requestLayout();
        invalidate();
    }

    public void clearChildBounds(View child) {
        boundsMap.remove(child);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutBounds b = boundsMap.getOrDefault(child, LayoutBounds.ZERO);
            int childW = Math.max(0, (int) Math.round(b.width()));
            int childH = Math.max(0, (int) Math.round(b.height()));
            child.measure(
                    MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY)
            );
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LayoutBounds bounds = boundsMap.getOrDefault(child, LayoutBounds.ZERO);
            int left = (int) Math.round(bounds.x());
            int top = (int) Math.round(bounds.y());
            int right = left + Math.max(0, (int) Math.round(bounds.width()));
            int bottom = top + Math.max(0, (int) Math.round(bounds.height()));
            child.layout(left, top, right, bottom);
        }
    }

    /**
     * Vertical ScrollView hosting an internal KairoLayoutHostGroup and reporting scroll offsets.
     */
    public static final class KairoScrollView extends ScrollView {
        public interface ScrollListener {
            void onScroll(int scrollX, int scrollY);
        }

        private final KairoLayoutHostGroup contentHost;
        private ScrollListener scrollListener;

        public KairoScrollView(Context context) {
            super(context);
            this.contentHost = new KairoLayoutHostGroup(context);
            setFillViewport(true);
            super.addView(
                    contentHost,
                    new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            );
        }

        public KairoLayoutHostGroup contentHost() {
            return contentHost;
        }

        public void setScrollListener(ScrollListener scrollListener) {
            this.scrollListener = scrollListener;
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (scrollListener != null && (l != oldl || t != oldt)) {
                scrollListener.onScroll(l, t);
            }
        }
    }
}
