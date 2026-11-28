package dev.kairo.platform.android;

import android.content.res.AssetManager;
import dev.kairo.resources.ResourceManager;
import dev.kairo.resources.ResourcePathValidator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Android AssetManager implementation of Kairo's neutral ResourceManager.Loader.
 * All paths pass through ResourcePathValidator before reaching AssetManager.
 */
public final class AndroidResourceLoader implements ResourceManager.Loader {
    private final AssetManager assetManager;
    private final String basePrefix;

    public AndroidResourceLoader(AssetManager assetManager) {
        this(assetManager, "kairo");
    }

    public AndroidResourceLoader(AssetManager assetManager, String basePrefix) {
        this.assetManager = assetManager;
        this.basePrefix = (basePrefix == null || basePrefix.isBlank())
                ? ""
                : ResourcePathValidator.validateAndNormalize(basePrefix) + "/";
    }

    @Override
    public Optional<ResourceManager.LocalResource> load(String normalizedPath) {
        String safeRelative = ResourcePathValidator.validateAndNormalize(normalizedPath);
        String assetPath = basePrefix + safeRelative;
        try (InputStream stream = assetManager.open(assetPath);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            byte[] bytes = out.toByteArray();
            String mime = ResourcePathValidator.inferMimeType(safeRelative);
            return Optional.of(new ResourceManager.LocalResource(safeRelative, bytes, mime));
        } catch (IOException notFound) {
            return Optional.empty();
        }
    }
}
