#include "kairo_render_engine.hpp"

#include <algorithm>
#include <cmath>
#include <cstring>

#if defined(__ANDROID__)
#include <android/bitmap.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <jni.h>
#endif

namespace kairo::render {

namespace {

inline float clamp01(float v) {
    return std::max(0.0f, std::min(1.0f, v));
}

// Analytical Signed Distance Function for a rounded box centered at origin.
inline float roundedBoxSdf(float px, float py, float halfW, float halfH, float radius) {
    const float r = std::min(radius, std::min(halfW, halfH));
    const float qx = std::fabs(px) - (halfW - r);
    const float qy = std::fabs(py) - (halfH - r);
    const float outX = std::max(qx, 0.0f);
    const float outY = std::max(qy, 0.0f);
    const float outsideDist = std::sqrt(outX * outX + outY * outY);
    const float insideDist = std::min(std::max(qx, qy), 0.0f);
    return outsideDist + insideDist - r;
}

inline uint32_t blendSrcOverRgba8888(uint32_t dstRgba, uint32_t srcArgb, float coverage) {
    const float srcA = ((srcArgb >> 24) & 0xFFu) / 255.0f * clamp01(coverage);
    if (srcA <= 0.001f) {
        return dstRgba;
    }

    const float srcR = ((srcArgb >> 16) & 0xFFu) / 255.0f;
    const float srcG = ((srcArgb >> 8) & 0xFFu) / 255.0f;
    const float srcB = (srcArgb & 0xFFu) / 255.0f;

    const float dstR = (dstRgba & 0xFFu) / 255.0f;
    const float dstG = ((dstRgba >> 8) & 0xFFu) / 255.0f;
    const float dstB = ((dstRgba >> 16) & 0xFFu) / 255.0f;
    const float dstA = ((dstRgba >> 24) & 0xFFu) / 255.0f;

    const float invSrcA = 1.0f - srcA;
    const float outA = srcA + dstA * invSrcA;
    const float outR = srcR * srcA + dstR * invSrcA;
    const float outG = srcG * srcA + dstG * invSrcA;
    const float outB = srcB * srcA + dstB * invSrcA;

    const uint32_t r8 = static_cast<uint32_t>(clamp01(outR) * 255.0f + 0.5f);
    const uint32_t g8 = static_cast<uint32_t>(clamp01(outG) * 255.0f + 0.5f);
    const uint32_t b8 = static_cast<uint32_t>(clamp01(outB) * 255.0f + 0.5f);
    const uint32_t a8 = static_cast<uint32_t>(clamp01(outA) * 255.0f + 0.5f);
    return (a8 << 24) | (b8 << 16) | (g8 << 8) | r8;
}

inline void rasterizeRoundedNode(
    const NativeRenderNode& node,
    float absX,
    float absY,
    float effectiveOpacity,
    const int32_t clipLeft,
    const int32_t clipTop,
    const int32_t clipRight,
    const int32_t clipBottom,
    uint32_t* pixels,
    int32_t stridePixels
) {
    const float w = node.bounds.width;
    const float h = node.bounds.height;
    if (w <= 0.0f || h <= 0.0f || effectiveOpacity <= 0.001f) {
        return;
    }

    const bool hasBg = ((node.bg_argb >> 24) & 0xFFu) > 0;
    const bool hasBorder = node.border_width > 0.0f && ((node.border_argb >> 24) & 0xFFu) > 0;
    if (!hasBg && !hasBorder) {
        return;
    }

    const int32_t minX = std::max(clipLeft, static_cast<int32_t>(std::floor(absX)));
    const int32_t minY = std::max(clipTop, static_cast<int32_t>(std::floor(absY)));
    const int32_t maxX = std::min(clipRight, static_cast<int32_t>(std::ceil(absX + w)));
    const int32_t maxY = std::min(clipBottom, static_cast<int32_t>(std::ceil(absY + h)));
    if (minX >= maxX || minY >= maxY) {
        return;
    }

    const float halfW = w * 0.5f;
    const float halfH = h * 0.5f;
    const float centerX = absX + halfW;
    const float centerY = absY + halfH;
    const float radius = std::max(0.0f, node.corner_radius);
    const float borderW = std::max(0.0f, node.border_width);

    for (int32_t py = minY; py < maxY; ++py) {
        uint32_t* row = pixels + static_cast<size_t>(py) * static_cast<size_t>(stridePixels);
        const float dy = (static_cast<float>(py) + 0.5f) - centerY;
        for (int32_t px = minX; px < maxX; ++px) {
            const float dx = (static_cast<float>(px) + 0.5f) - centerX;
            const float dist = roundedBoxSdf(dx, dy, halfW, halfH, radius);
            const float outerCov = clamp01(0.5f - dist);
            if (outerCov <= 0.001f) {
                continue;
            }

            uint32_t dst = row[px];
            if (hasBg) {
                dst = blendSrcOverRgba8888(dst, node.bg_argb, outerCov * effectiveOpacity);
            }
            if (hasBorder) {
                const float innerCov = clamp01(0.5f - (dist + borderW));
                const float ringCov = clamp01(outerCov - innerCov);
                if (ringCov > 0.001f) {
                    dst = blendSrcOverRgba8888(dst, node.border_argb, ringCov * effectiveOpacity);
                }
            }
            row[px] = dst;
        }
    }
}

} // namespace

void KairoSceneGraph::upsertNode(
    int64_t id,
    NativeRect bounds,
    uint32_t bgArgb,
    uint32_t borderArgb,
    float borderWidth,
    float cornerRadius,
    float opacity,
    uint32_t visualFlags,
    bool clipChildren,
    bool visible
) {
    NativeRenderNode& node = nodes_[id];
    node.id = id;
    node.bounds = bounds;
    node.bg_argb = bgArgb;
    node.border_argb = borderArgb;
    node.border_width = borderWidth;
    node.corner_radius = cornerRadius;
    node.opacity = clamp01(opacity);
    node.visual_flags = visualFlags;
    node.clip_children = clipChildren;
    node.visible = visible;
}

void KairoSceneGraph::setChildren(int64_t parentId, const int64_t* childIds, int32_t count) {
    auto it = nodes_.find(parentId);
    if (it == nodes_.end()) {
        return;
    }
    it->second.children.assign(childIds, childIds + std::max(0, count));
}

void KairoSceneGraph::removeNode(int64_t id) {
    for (auto& [parentId, parentNode] : nodes_) {
        auto& children = parentNode.children;
        children.erase(std::remove(children.begin(), children.end(), id), children.end());
    }
    removeNodeRecursive(id);
}

void KairoSceneGraph::removeNodeRecursive(int64_t id) {
    auto it = nodes_.find(id);
    if (it == nodes_.end()) {
        if (root_id_ == id) {
            root_id_ = -1;
        }
        return;
    }
    std::vector<int64_t> childrenCopy = std::move(it->second.children);
    nodes_.erase(it);
    if (root_id_ == id) {
        root_id_ = -1;
    }
    for (int64_t childId : childrenCopy) {
        removeNodeRecursive(childId);
    }
}

void KairoSceneGraph::setRoot(int64_t rootId) {
    root_id_ = rootId;
}

void KairoSceneGraph::clear() {
    nodes_.clear();
    root_id_ = -1;
}

int64_t KairoSceneGraph::hitTest(float x, float y) const {
    if (root_id_ < 0) {
        return -1;
    }
    return hitTestRecursive(root_id_, x, y, 0.0f, 0.0f);
}

int64_t KairoSceneGraph::hitTestRecursive(
    int64_t nodeId,
    float x,
    float y,
    float offsetX,
    float offsetY
) const {
    auto it = nodes_.find(nodeId);
    if (it == nodes_.end()) {
        return -1;
    }
    const NativeRenderNode& node = it->second;
    if (!node.visible || (node.visual_flags & FLAG_DISABLED) != 0u) {
        return -1;
    }

    const float absX = offsetX + node.bounds.x;
    const float absY = offsetY + node.bounds.y;
    const bool inside = x >= absX && x <= (absX + node.bounds.width)
                     && y >= absY && y <= (absY + node.bounds.height);

    if (node.clip_children && !inside) {
        return -1;
    }

    for (auto rit = node.children.rbegin(); rit != node.children.rend(); ++rit) {
        int64_t childHit = hitTestRecursive(*rit, x, y, absX, absY);
        if (childHit >= 0) {
            return childHit;
        }
    }

    return inside ? node.id : -1;
}

void KairoSceneGraph::renderToRgba8888(
    uint32_t* pixels,
    int32_t bufferWidth,
    int32_t bufferHeight,
    int32_t stridePixels
) const {
    if (!pixels || bufferWidth <= 0 || bufferHeight <= 0 || stridePixels < bufferWidth) {
        return;
    }
    if (root_id_ < 0) {
        return;
    }
    const ClipRect viewportClip{0, 0, bufferWidth, bufferHeight};
    renderNodeRecursive(root_id_, 0.0f, 0.0f, 1.0f, viewportClip, pixels, stridePixels);
}

void KairoSceneGraph::renderNodeRecursive(
    int64_t nodeId,
    float offsetX,
    float offsetY,
    float inheritedOpacity,
    ClipRect clip,
    uint32_t* pixels,
    int32_t stridePixels
) const {
    auto it = nodes_.find(nodeId);
    if (it == nodes_.end()) {
        return;
    }
    const NativeRenderNode& node = it->second;
    if (!node.visible) {
        return;
    }

    const float absX = offsetX + node.bounds.x;
    const float absY = offsetY + node.bounds.y;
    const float effectiveOpacity = inheritedOpacity * node.opacity;

    rasterizeRoundedNode(
        node,
        absX,
        absY,
        effectiveOpacity,
        clip.left,
        clip.top,
        clip.right,
        clip.bottom,
        pixels,
        stridePixels
    );

    ClipRect nextClip = clip;
    if (node.clip_children) {
        nextClip.left = std::max(clip.left, static_cast<int32_t>(std::floor(absX)));
        nextClip.top = std::max(clip.top, static_cast<int32_t>(std::floor(absY)));
        nextClip.right = std::min(clip.right, static_cast<int32_t>(std::ceil(absX + node.bounds.width)));
        nextClip.bottom = std::min(clip.bottom, static_cast<int32_t>(std::ceil(absY + node.bounds.height)));
        if (nextClip.left >= nextClip.right || nextClip.top >= nextClip.bottom) {
            return;
        }
    }

    for (int64_t childId : node.children) {
        renderNodeRecursive(childId, absX, absY, effectiveOpacity, nextClip, pixels, stridePixels);
    }
}

} // namespace kairo::render

#if defined(__ANDROID__)
extern "C" {

JNIEXPORT jlong JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeCreateScene(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new kairo::render::KairoSceneGraph());
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeDestroyScene(JNIEnv*, jclass, jlong handle) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    delete graph;
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeClear(JNIEnv*, jclass, jlong handle) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (graph) graph->clear();
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeSetRoot(JNIEnv*, jclass, jlong handle, jlong rootId) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (graph) graph->setRoot(static_cast<int64_t>(rootId));
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeUpsertNode(
    JNIEnv*, jclass, jlong handle, jlong id,
    jfloat x, jfloat y, jfloat width, jfloat height,
    jint bgArgb, jint borderArgb, jfloat borderWidth, jfloat cornerRadius,
    jfloat opacity, jint visualFlags, jboolean clipChildren, jboolean visible
) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (!graph) return;
    graph->upsertNode(
        static_cast<int64_t>(id),
        kairo::render::NativeRect{x, y, width, height},
        static_cast<uint32_t>(bgArgb),
        static_cast<uint32_t>(borderArgb),
        borderWidth,
        cornerRadius,
        opacity,
        static_cast<uint32_t>(visualFlags),
        clipChildren == JNI_TRUE,
        visible == JNI_TRUE
    );
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeSetChildren(
    JNIEnv* env, jclass, jlong handle, jlong parentId, jlongArray childIds
) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (!graph || !childIds) return;
    const jsize len = env->GetArrayLength(childIds);
    jlong* elems = env->GetLongArrayElements(childIds, nullptr);
    if (elems) {
        static_assert(sizeof(jlong) == sizeof(int64_t), "jlong must match int64_t");
        graph->setChildren(static_cast<int64_t>(parentId), reinterpret_cast<const int64_t*>(elems), len);
        env->ReleaseLongArrayElements(childIds, elems, JNI_ABORT);
    }
}

JNIEXPORT void JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeRemoveNode(JNIEnv*, jclass, jlong handle, jlong id) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (graph) graph->removeNode(static_cast<int64_t>(id));
}

JNIEXPORT jlong JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeHitTest(JNIEnv*, jclass, jlong handle, jfloat x, jfloat y) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    return graph ? static_cast<jlong>(graph->hitTest(x, y)) : -1L;
}

JNIEXPORT jboolean JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeRenderToBitmap(JNIEnv* env, jclass, jlong handle, jobject bitmap) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (!graph || !bitmap) return JNI_FALSE;

    AndroidBitmapInfo info{};
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }
    void* pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS || !pixels) {
        return JNI_FALSE;
    }
    std::memset(pixels, 0, static_cast<size_t>(info.stride) * static_cast<size_t>(info.height));
    graph->renderToRgba8888(
        static_cast<uint32_t*>(pixels),
        static_cast<int32_t>(info.width),
        static_cast<int32_t>(info.height),
        static_cast<int32_t>(info.stride / 4u)
    );
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_dev_kairo_renderer_android_KairoNativeEngine_nativeRenderToSurface(JNIEnv* env, jclass, jlong handle, jobject surface) {
    auto* graph = reinterpret_cast<kairo::render::KairoSceneGraph*>(handle);
    if (!graph || !surface) return JNI_FALSE;

    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;

    ANativeWindow_Buffer buffer{};
    if (ANativeWindow_lock(window, &buffer, nullptr) != 0) {
        ANativeWindow_release(window);
        return JNI_FALSE;
    }
    if (buffer.bits && (buffer.format == WINDOW_FORMAT_RGBA_8888 || buffer.format == WINDOW_FORMAT_RGBX_8888)) {
        std::memset(buffer.bits, 0, static_cast<size_t>(buffer.stride) * static_cast<size_t>(buffer.height) * 4u);
        graph->renderToRgba8888(
            static_cast<uint32_t*>(buffer.bits),
            buffer.width,
            buffer.height,
            buffer.stride
        );
    }
    ANativeWindow_unlockAndPost(window);
    ANativeWindow_release(window);
    return JNI_TRUE;
}

} // extern "C"
#endif
