package dev.kairo.resources;

import dev.kairo.core.Result;
import dev.kairo.core.SourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Local resource registry and bounded LRU cache with strict path-traversal & remote-URI rejection.
 * Platform hosts (such as Android AssetManager) plug in via {@link Loader} without leaking platform types.
 */
public final class ResourceManager {
    public record LocalResource(String path, byte[] bytes, String mimeType) {
        public LocalResource {
            bytes = bytes != null ? bytes.clone() : new byte[0];
            mimeType = (mimeType == null || mimeType.isBlank())
                    ? ResourcePathValidator.inferMimeType(path)
                    : mimeType;
        }

        public byte[] copyBytes() {
            return bytes.clone();
        }

        public int byteSize() {
            return bytes.length;
        }

        public String asUtf8String() {
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @FunctionalInterface
    public interface Loader {
        Optional<LocalResource> load(String normalizedPath);

        static Loader fromMap(Map<String, LocalResource> resources) {
            Objects.requireNonNull(resources, "resources map cannot be null");
            Map<String, LocalResource> copy = new LinkedHashMap<>();
            resources.forEach((rawKey, res) -> copy.put(validateAndNormalize(rawKey), res));
            return normalized -> Optional.ofNullable(copy.get(normalized));
        }

        static Loader classpath(ClassLoader classLoader, String basePrefix) {
            Objects.requireNonNull(classLoader, "classLoader cannot be null");
            String prefix = (basePrefix == null || basePrefix.isBlank())
                    ? ""
                    : validateAndNormalize(basePrefix) + "/";
            return normalized -> {
                String fullResourcePath = prefix + normalized;
                try (InputStream stream = classLoader.getResourceAsStream(fullResourcePath)) {
                    if (stream == null) {
                        return Optional.empty();
                    }
                    byte[] data = stream.readAllBytes();
                    return Optional.of(new LocalResource(
                            normalized,
                            data,
                            ResourcePathValidator.inferMimeType(normalized)
                    ));
                } catch (IOException ex) {
                    throw new IllegalStateException("Failed reading bundled resource: " + normalized, ex);
                }
            };
        }
    }

    public static final class InMemoryLoaderBuilder {
        private final Map<String, LocalResource> entries = new LinkedHashMap<>();

        public InMemoryLoaderBuilder putBytes(String path, byte[] bytes, String mimeType) {
            String normalized = validateAndNormalize(path);
            entries.put(normalized, new LocalResource(normalized, bytes, mimeType));
            return this;
        }

        public InMemoryLoaderBuilder putText(String path, String utf8Content, String mimeType) {
            byte[] bytes = utf8Content != null ? utf8Content.getBytes(StandardCharsets.UTF_8) : new byte[0];
            return putBytes(path, bytes, mimeType);
        }

        public Loader build() {
            return Loader.fromMap(entries);
        }
    }

    private final Loader loader;
    private final ResourceCache cache;

    public ResourceManager(Loader loader) {
        this(loader, ResourceCache.DEFAULT_MAX_BYTES, ResourceCache.DEFAULT_MAX_ENTRIES);
    }

    public ResourceManager(Loader loader, long maxCacheBytes) {
        this(loader, maxCacheBytes, ResourceCache.DEFAULT_MAX_ENTRIES);
    }

    public ResourceManager(Loader loader, long maxCacheBytes, int maxCacheEntries) {
        this.loader = Objects.requireNonNull(loader, "loader cannot be null");
        this.cache = new ResourceCache(maxCacheBytes, maxCacheEntries);
    }

    public static InMemoryLoaderBuilder inMemoryBuilder() {
        return new InMemoryLoaderBuilder();
    }

    public Optional<LocalResource> resolve(String rawPath) {
        String normalized = validateAndNormalize(rawPath);
        LocalResource cached = cache.get(normalized);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<LocalResource> loaded = loader.load(normalized);
        if (loaded.isEmpty()) {
            return Optional.empty();
        }
        LocalResource res = loaded.get();
        LocalResource normalizedRes = normalized.equals(res.path())
                ? res
                : new LocalResource(normalized, res.bytes(), res.mimeType());
        cache.put(normalized, normalizedRes);
        return Optional.of(normalizedRes);
    }

    public Result<LocalResource> resolveResult(String rawPath) {
        SourceLocation loc = new SourceLocation(rawPath == null ? "<null>" : rawPath, 0, 0);
        final String normalized;
        try {
            normalized = validateAndNormalize(rawPath);
        } catch (IllegalArgumentException ex) {
            return Result.error("RES_EMPTY_PATH", ex.getMessage(), loc);
        } catch (SecurityException ex) {
            return Result.error("RES_FORBIDDEN_PATH", ex.getMessage(), loc);
        }
        return resolve(normalized)
                .<Result<LocalResource>>map(Result::ok)
                .orElseGet(() -> Result.error("RES_NOT_FOUND", "Local resource not found: " + normalized, loc));
    }

    public Optional<String> resolveText(String rawPath) {
        return resolve(rawPath).map(LocalResource::asUtf8String);
    }

    public int preload(String... paths) {
        if (paths == null) return 0;
        int loadedCount = 0;
        for (String path : paths) {
            if (resolve(path).isPresent()) {
                loadedCount++;
            }
        }
        return loadedCount;
    }

    public boolean invalidate(String rawPath) {
        return cache.invalidate(validateAndNormalize(rawPath));
    }

    public void clearCache() {
        cache.clear();
    }

    public ResourceCache.CacheMetrics metrics() {
        return cache.metrics();
    }

    public static String validateAndNormalize(String rawPath) {
        return ResourcePathValidator.validateAndNormalize(rawPath);
    }
}
