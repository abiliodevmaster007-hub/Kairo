package dev.kairo.renderer.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import dev.kairo.core.KairoId;
import dev.kairo.events.UIEvent;
import dev.kairo.layout.LayoutEngine;
import dev.kairo.renderer.api.RenderEngineStats;
import dev.kairo.renderer.api.RenderHandle;
import dev.kairo.renderer.api.RenderPatch;
import dev.kairo.renderer.api.Renderer;
import dev.kairo.resources.ResourceManager;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * ONLY module in Kairo allowed to touch android.view.View / android.widget.* / C++ JNI for rendering.
 * Delegates rendering coordination to {@link KairoRenderEngine}, enforcing strict v1/v2 boundary contracts.
 */
public final class AndroidRenderer implements Renderer {
    public static final class AndroidViewHandle implements RenderHandle {
        private final KairoId id;
        private final String componentType;
        final View view;

        AndroidViewHandle(KairoId id, String componentType, View view) {
            this.id = id;
            this.componentType = componentType;
            this.view = view;
        }

        @Override
        public KairoId id() { return id; }

        @Override
        public String componentType() { return componentType; }
    }

    private final KairoRenderEngine engine;

    public AndroidRenderer(Context androidContext) {
        this(androidContext, null);
    }

    public AndroidRenderer(Context androidContext, ResourceManager resourceManager) {
        this.engine = new KairoRenderEngine(androidContext, resourceManager);
    }

    public ViewGroup rootContainerView() {
        return engine.rootContainerView();
    }

    public KairoRenderEngine renderEngine() {
        return engine;
    }

    public KairoImeBridge imeBridge() {
        return engine.imeBridge();
    }

    public boolean isNativeAccelerated() {
        return engine.isNativeAccelerated();
    }

    @Override
    public Capabilities capabilities() {
        return new Capabilities(
                false,
                true,
                true,
                engine.isNativeAccelerated() ? "android-views+cpp-sdf-v1" : "android-views-canvas-v1"
        );
    }

    @Override
    public LayoutEngine.LeafMeasurer leafMeasurer() {
        return engine.leafMeasurer();
    }

    @Override
    public RenderHandle mountRoot(KairoId rootId, String componentType, Consumer<UIEvent> eventSink) {
        return engine.mountRoot(rootId, componentType, eventSink);
    }

    @Override
    public void apply(List<RenderPatch> patchBatch) {
        engine.applyBatch(patchBatch);
    }

    @Override
    public void unmountRoot(KairoId rootId) {
        engine.unmountRoot();
    }

    @Override
    public Set<KairoId> mountedIds() {
        return engine.mountedIds();
    }

    @Override
    public RendererTreeSnapshot inspectTree() {
        return engine.inspectTree();
    }

    @Override
    public RenderEngineStats engineStats() {
        return engine.stats();
    }

    @Override
    public void flushFrame() {
        engine.rootContainerView().requestLayout();
        engine.rootContainerView().invalidate();
    }

    public void destroy() {
        engine.destroy();
    }
}
