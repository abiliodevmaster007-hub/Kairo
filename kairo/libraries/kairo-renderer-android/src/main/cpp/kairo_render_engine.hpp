#pragma once

#include <cstdint>
#include <unordered_map>
#include <vector>

namespace kairo::render {

struct NativeRect {
    float x = 0.0f;
    float y = 0.0f;
    float width = 0.0f;
    float height = 0.0f;
};

enum VisualFlags : uint32_t {
    FLAG_NONE     = 0u,
    FLAG_DISABLED = 1u << 0,
    FLAG_FOCUSED  = 1u << 1,
    FLAG_PRESSED  = 1u << 2,
    FLAG_ERROR    = 1u << 3,
    FLAG_CHECKED  = 1u << 4,
    FLAG_SELECTED = 1u << 5
};

struct NativeRenderNode {
    int64_t id = 0;
    NativeRect bounds{};
    uint32_t bg_argb = 0x00000000u;
    uint32_t border_argb = 0x00000000u;
    float border_width = 0.0f;
    float corner_radius = 0.0f;
    float opacity = 1.0f;
    uint32_t visual_flags = FLAG_NONE;
    bool clip_children = false;
    bool visible = true;
    std::vector<int64_t> children;
};

/**
 * Cache-friendly native C++17 Scene Graph & SDF 2D UI rasterizer.
 * Isolated exclusively inside kairo-renderer-android so neutral Java modules
 * remain 100% platform-independent.
 */
class KairoSceneGraph {
public:
    void upsertNode(
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
    );

    void setChildren(int64_t parentId, const int64_t* childIds, int32_t count);
    void removeNode(int64_t id);
    void setRoot(int64_t rootId);
    void clear();

    int64_t hitTest(float x, float y) const;
    void renderToRgba8888(uint32_t* pixels, int32_t bufferWidth, int32_t bufferHeight, int32_t stridePixels) const;

    size_t nodeCount() const { return nodes_.size(); }

private:
    struct ClipRect {
        int32_t left;
        int32_t top;
        int32_t right;
        int32_t bottom;
    };

    void renderNodeRecursive(
        int64_t nodeId,
        float offsetX,
        float offsetY,
        float inheritedOpacity,
        ClipRect clip,
        uint32_t* pixels,
        int32_t stridePixels
    ) const;

    void removeNodeRecursive(int64_t id);

    int64_t hitTestRecursive(int64_t nodeId, float localX, float localY, float offsetX, float offsetY) const;

    int64_t root_id_ = -1;
    std::unordered_map<int64_t, NativeRenderNode> nodes_;
};

} // namespace kairo::render
