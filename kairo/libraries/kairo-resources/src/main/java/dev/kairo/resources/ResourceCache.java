package dev.kairo.resources;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe bounded LRU cache for local resources with both byte-budget and entry limits.
 */
public final class ResourceCache {
    public record CacheMetrics(
            long hits,
            long misses,
            long evictions,
            long currentBytes,
            int entryCount
    ) {}

    public static final long DEFAULT_MAX_BYTES = 16L * 1024L * 1024L;
    public static final int DEFAULT_MAX_ENTRIES = 512;

    private final long maxBytes;
    private final int maxEntries;
    private final Map<String, ResourceManager.LocalResource> entries = new LinkedHashMap<>(16, 0.75f, true);

    private long currentBytes;
    private long hits;
    private long misses;
    private long evictions;

    public ResourceCache() {
        this(DEFAULT_MAX_BYTES, DEFAULT_MAX_ENTRIES);
    }

    public ResourceCache(long maxBytes, int maxEntries) {
        if (maxBytes <= 0 || maxEntries <= 0) {
            throw new IllegalArgumentException("Cache bounds must be positive");
        }
        this.maxBytes = maxBytes;
        this.maxEntries = maxEntries;
    }

    public synchronized ResourceManager.LocalResource get(String normalizedPath) {
        ResourceManager.LocalResource found = entries.get(normalizedPath);
        if (found != null) {
            hits++;
        } else {
            misses++;
        }
        return found;
    }

    public synchronized void put(String normalizedPath, ResourceManager.LocalResource resource) {
        if (resource == null) return;
        long size = resource.byteSize();
        if (size > maxBytes) {
            return;
        }
        ResourceManager.LocalResource previous = entries.remove(normalizedPath);
        if (previous != null) {
            currentBytes -= previous.byteSize();
        }
        entries.put(normalizedPath, resource);
        currentBytes += size;
        evictIfNeeded();
    }

    public synchronized boolean invalidate(String normalizedPath) {
        ResourceManager.LocalResource removed = entries.remove(normalizedPath);
        if (removed != null) {
            currentBytes -= removed.byteSize();
            return true;
        }
        return false;
    }

    public synchronized void clear() {
        entries.clear();
        currentBytes = 0;
    }

    public synchronized CacheMetrics metrics() {
        return new CacheMetrics(hits, misses, evictions, currentBytes, entries.size());
    }

    private void evictIfNeeded() {
        Iterator<Map.Entry<String, ResourceManager.LocalResource>> it = entries.entrySet().iterator();
        while ((currentBytes > maxBytes || entries.size() > maxEntries) && it.hasNext()) {
            Map.Entry<String, ResourceManager.LocalResource> eldest = it.next();
            currentBytes -= eldest.getValue().byteSize();
            it.remove();
            evictions++;
        }
    }
}
