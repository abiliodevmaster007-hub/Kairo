package dev.kairo.renderer.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Hardware/NDK SurfaceView host backed by KairoRenderEngine's C++17 SDF scene-graph rasterizer,
 * falling back cleanly to Android Canvas when the native .so is not bundled.
 */
public final class KairoNativeSurfaceView extends SurfaceView implements SurfaceHolder.Callback {
    private final KairoRenderEngine engine;
    private boolean surfaceReady;

    public KairoNativeSurfaceView(Context context, KairoRenderEngine engine) {
        super(context);
        this.engine = engine;
        getHolder().addCallback(this);
    }

    public boolean renderFrame() {
        if (!surfaceReady) {
            return false;
        }
        SurfaceHolder holder = getHolder();
        if (engine.isNativeAccelerated() && engine.renderNativeSceneToSurface(holder.getSurface())) {
            return true;
        }
        Canvas canvas = holder.lockCanvas();
        if (canvas == null) {
            return false;
        }
        try {
            drawFallbackBackdrop(canvas);
        } finally {
            holder.unlockCanvasAndPost(canvas);
        }
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        renderFrame();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        renderFrame();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
    }

    private void drawFallbackBackdrop(Canvas canvas) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF0B0B0E);
        canvas.drawRect(new RectF(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
    }
}
