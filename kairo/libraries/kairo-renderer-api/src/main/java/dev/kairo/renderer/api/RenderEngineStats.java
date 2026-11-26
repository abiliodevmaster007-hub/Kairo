package dev.kairo.renderer.api;

/**
 * Neutral snapshot of renderer engine metrics (frame commit timing, backend type, native acceleration).
 */
public record RenderEngineStats(
        String backendName,
        boolean nativeAccelerated,
        int mountedNodes,
        int lastPatchBatchSize,
        long totalPatchesApplied,
        long lastFrameDurationMicros
) {
    public static RenderEngineStats empty(String backendName) {
        return new RenderEngineStats(backendName, false, 0, 0, 0L, 0L);
    }
}
