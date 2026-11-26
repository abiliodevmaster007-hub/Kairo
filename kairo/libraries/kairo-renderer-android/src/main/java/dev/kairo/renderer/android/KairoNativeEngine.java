package dev.kairo.renderer.android;

import android.graphics.Bitmap;
import android.view.Surface;

/**
 * Optional JNI bridge to the C++17 native KairoSceneGraph & SDF rasterizer (libkairo_render.so).
 * Gracefully degrades to false/no-op when running on pure Java/View environments without NDK .so.
 */
public final class KairoNativeEngine implements AutoCloseable {
    private static final boolean LIBRARY_LOADED;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("kairo_render");
            loaded = true;
        } catch (UnsatisfiedLinkError | SecurityException ignored) {
            loaded = false;
        }
        LIBRARY_LOADED = loaded;
    }

    private long nativeHandle;

    public KairoNativeEngine() {
        this.nativeHandle = LIBRARY_LOADED ? nativeCreateScene() : 0L;
    }

    public static boolean isNativeLibraryLoaded() {
        return LIBRARY_LOADED;
    }

    public boolean isActive() {
        return LIBRARY_LOADED && nativeHandle != 0L;
    }

    public void clear() {
        if (isActive()) {
            nativeClear(nativeHandle);
        }
    }

    public void setRoot(long rootId) {
        if (isActive()) {
            nativeSetRoot(nativeHandle, rootId);
        }
    }

    public void upsertNode(long id, KairoSceneEncoder.EncodedVisualNode encoded) {
        if (!isActive() || encoded == null) return;
        nativeUpsertNode(
                nativeHandle,
                id,
                (float) encoded.bounds().x(),
                (float) encoded.bounds().y(),
                (float) encoded.bounds().width(),
                (float) encoded.bounds().height(),
                encoded.backgroundArgb(),
                encoded.borderArgb(),
                encoded.borderWidthPx(),
                encoded.cornerRadiusPx(),
                encoded.opacity(),
                encoded.visualFlags(),
                encoded.clipChildren(),
                encoded.visible()
        );
    }

    public void setChildren(long parentId, long[] childIds) {
        if (isActive() && childIds != null) {
            nativeSetChildren(nativeHandle, parentId, childIds);
        }
    }

    public void removeNode(long id) {
        if (isActive()) {
            nativeRemoveNode(nativeHandle, id);
        }
    }

    public long hitTest(float x, float y) {
        return isActive() ? nativeHitTest(nativeHandle, x, y) : -1L;
    }

    public boolean renderToBitmap(Bitmap bitmap) {
        return isActive() && bitmap != null && nativeRenderToBitmap(nativeHandle, bitmap);
    }

    public boolean renderToSurface(Surface surface) {
        return isActive() && surface != null && nativeRenderToSurface(nativeHandle, surface);
    }

    @Override
    public void close() {
        if (nativeHandle != 0L) {
            if (LIBRARY_LOADED) {
                nativeDestroyScene(nativeHandle);
            }
            nativeHandle = 0L;
        }
    }

    private static native long nativeCreateScene();
    private static native void nativeDestroyScene(long handle);
    private static native void nativeClear(long handle);
    private static native void nativeSetRoot(long handle, long rootId);
    private static native void nativeUpsertNode(
            long handle,
            long id,
            float x,
            float y,
            float width,
            float height,
            int bgArgb,
            int borderArgb,
            float borderWidth,
            float cornerRadius,
            float opacity,
            int visualFlags,
            boolean clipChildren,
            boolean visible
    );
    private static native void nativeSetChildren(long handle, long parentId, long[] childIds);
    private static native void nativeRemoveNode(long handle, long id);
    private static native long nativeHitTest(long handle, float x, float y);
    private static native boolean nativeRenderToBitmap(long handle, Bitmap bitmap);
    private static native boolean nativeRenderToSurface(long handle, Surface surface);
}
