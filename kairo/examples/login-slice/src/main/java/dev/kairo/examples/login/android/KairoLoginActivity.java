package dev.kairo.examples.login.android;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.components.basic.BasicComponents;
import dev.kairo.components.input.InputComponents;
import dev.kairo.css.CssStylesheet;
import dev.kairo.css.KairoCssParser;
import dev.kairo.examples.login.LoginScreenModel;
import dev.kairo.layout.LayoutConstraints;
import dev.kairo.renderer.android.AndroidRenderer;
import dev.kairo.runtime.KairoRuntime;
import dev.kairo.xml.KairoXmlParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Thin Android Activity host proving the Stage 2 (A.1) vertical slice on an Android device.
 * All UI logic, state, styles and layout run in neutral Java modules; only rendering uses AndroidRenderer.
 */
public final class KairoLoginActivity extends Activity {
    private KairoRuntime runtime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ComponentRegistry registry = new ComponentRegistry();
        BasicComponents.registerAll(registry);
        InputComponents.registerAll(registry);

        AndroidRenderer renderer = new AndroidRenderer(this);
        runtime = new KairoRuntime(registry, renderer);

        String xml = readAsset("kairo/login.xml");
        String css = readAsset("kairo/login.css");

        var parsed = new KairoXmlParser().parse("login.xml", xml);
        if (parsed.diagnostics().hasErrors()) {
            throw new IllegalStateException("Login XML errors: " + parsed.diagnostics().all());
        }
        CssStylesheet stylesheet = new KairoCssParser().parse(css);

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        LayoutConstraints viewport = LayoutConstraints.fixed(metrics.widthPixels, metrics.heightPixels);

        runtime.mount(parsed.root(), stylesheet, new LoginScreenModel(), viewport);
        setContentView(renderer.rootContainerView());
    }

    @Override
    protected void onDestroy() {
        if (runtime != null) {
            runtime.destroy();
        }
        super.onDestroy();
    }

    private String readAsset(String path) {
        try (InputStream in = getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Missing asset: " + path, ex);
        }
    }
}
