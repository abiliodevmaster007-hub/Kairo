package dev.kairo.platform.android;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.core.CapabilityRegistry;
import dev.kairo.lifecycle.LifecycleScope;
import dev.kairo.navigation.Router;
import dev.kairo.renderer.android.AndroidRenderer;
import dev.kairo.resources.ResourceManager;
import dev.kairo.runtime.KairoRuntime;

/**
 * Isolates Android OS host services (main Looper scheduler, AssetManager, clipboard,
 * back navigation, lifecycle bridge) behind neutral Kairo contracts.
 */
public final class AndroidPlatformHost {
    public static final class AndroidClipboardCapability implements CapabilityRegistry.Capability {
        private final Context context;

        public AndroidClipboardCapability(Context context) {
            this.context = context;
        }

        @Override
        public String id() {
            return "clipboard";
        }

        @Override
        public boolean isAvailable() {
            return context.getSystemService(Context.CLIPBOARD_SERVICE) != null;
        }

        public void copyText(String label, String text) {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText(label, text));
            }
        }
    }

    public static final class AndroidAssetsCapability implements CapabilityRegistry.Capability {
        private final ResourceManager resourceManager;

        public AndroidAssetsCapability(Context context) {
            this.resourceManager = new ResourceManager(new AndroidResourceLoader(context.getAssets()));
        }

        @Override
        public String id() {
            return "local-assets";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        public ResourceManager resourceManager() {
            return resourceManager;
        }
    }

    public record MountedHost(
            AndroidRenderer renderer,
            KairoRuntime runtime,
            ResourceManager resourceManager,
            CapabilityRegistry capabilities
    ) {
        public void destroy() {
            runtime.destroy();
            renderer.destroy();
        }
    }

    public MountedHost bootstrap(Context context, ComponentRegistry registry) {
        AndroidMainScheduler.install();
        CapabilityRegistry capabilities = new CapabilityRegistry();
        capabilities.register(new AndroidClipboardCapability(context));
        AndroidAssetsCapability assetsCap = new AndroidAssetsCapability(context);
        capabilities.register(assetsCap);

        AndroidRenderer renderer = new AndroidRenderer(context, assetsCap.resourceManager());
        KairoRuntime runtime = new KairoRuntime(registry, renderer);
        return new MountedHost(renderer, runtime, assetsCap.resourceManager(), capabilities);
    }

    public boolean onSystemBackPressed(Router router) {
        return router != null && router.back();
    }

    public void onHostUnmount(LifecycleScope scope) {
        if (scope != null) {
            scope.onUnmount();
        }
    }

    public void onHostDestroy(LifecycleScope scope) {
        if (scope != null) {
            scope.onDestroy();
        }
    }
}
