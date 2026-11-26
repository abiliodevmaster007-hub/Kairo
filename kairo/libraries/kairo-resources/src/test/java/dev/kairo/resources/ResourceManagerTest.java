package dev.kairo.resources;

import dev.kairo.core.Result;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

final class ResourceManagerTest {

    @Test
    void normalizesRelativePathsAndInfersMimeTypes() {
        ResourceManager manager = new ResourceManager(
                ResourceManager.inMemoryBuilder()
                        .putBytes("images/logo.png", new byte[]{1, 2, 3, 4}, null)
                        .putText("themes/dark.kcss", "Screen { background: #0B0B0E; }", null)
                        .build()
        );

        ResourceManager.LocalResource logo = manager.resolve("/images/./logo.png").orElseThrow();
        assertThat(logo.path()).isEqualTo("images/logo.png");
        assertThat(logo.mimeType()).isEqualTo("image/png");
        assertThat(logo.byteSize()).isEqualTo(4);

        ResourceManager.LocalResource theme = manager.resolve("themes\\dark.kcss").orElseThrow();
        assertThat(theme.path()).isEqualTo("themes/dark.kcss");
        assertThat(theme.mimeType()).isEqualTo("text/kairo-style");
        assertThat(manager.resolveText("themes/dark.kcss")).contains("Screen { background: #0B0B0E; }");
    }

    @Test
    void rejectsTraversalRemoteUrlsAndEncodedObfuscation() {
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("../secrets.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("assets/../../etc/passwd"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("%2e%2e/secrets.txt"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("https://cdn.example.com/avatar.png"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("file:///etc/hosts"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("C:\\Windows\\system.ini"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("icons/logo.png\0.jpg"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> ResourceManager.validateAndNormalize("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesBoundedLruCacheEvictionByByteBudget() {
        AtomicInteger loads = new AtomicInteger();
        ResourceManager manager = new ResourceManager(path -> {
            loads.incrementAndGet();
            return switch (path) {
                case "a.bin" -> java.util.Optional.of(new ResourceManager.LocalResource(path, new byte[6], "application/octet-stream"));
                case "b.bin" -> java.util.Optional.of(new ResourceManager.LocalResource(path, new byte[6], "application/octet-stream"));
                case "c.bin" -> java.util.Optional.of(new ResourceManager.LocalResource(path, new byte[6], "application/octet-stream"));
                default -> java.util.Optional.empty();
            };
        }, 12L, 10);

        manager.resolve("a.bin");
        manager.resolve("b.bin");
        // Access a.bin again so b.bin becomes least recently used
        manager.resolve("a.bin");
        assertThat(loads.get()).isEqualTo(2);

        // Adding c.bin (6 bytes) exceeds 12-byte budget -> evicts b.bin
        manager.resolve("c.bin");
        assertThat(manager.metrics().currentBytes()).isEqualTo(12L);
        assertThat(manager.metrics().evictions()).isEqualTo(1L);

        // a.bin is still cached (no new load); b.bin must reload
        manager.resolve("a.bin");
        assertThat(loads.get()).isEqualTo(3);
        manager.resolve("b.bin");
        assertThat(loads.get()).isEqualTo(4);
    }

    @Test
    void defensiveByteCopiesPreventExternalMutationOfCache() {
        byte[] source = "hello".getBytes(StandardCharsets.UTF_8);
        ResourceManager manager = new ResourceManager(
                ResourceManager.inMemoryBuilder()
                        .putBytes("data/msg.txt", source, "text/plain")
                        .build()
        );
        source[0] = 'X';

        ResourceManager.LocalResource loaded = manager.resolve("data/msg.txt").orElseThrow();
        assertThat(loaded.asUtf8String()).isEqualTo("hello");
    }

    @Test
    void returnsStructuredResultDiagnosticsOnMissingOrForbiddenPaths() {
        ResourceManager manager = new ResourceManager(
                ResourceManager.inMemoryBuilder()
                        .putText("ok.txt", "kairo", "text/plain")
                        .build()
        );

        Result<ResourceManager.LocalResource> ok = manager.resolveResult("ok.txt");
        assertThat(ok.isOk()).isTrue();

        Result<ResourceManager.LocalResource> missing = manager.resolveResult("missing.png");
        if (!(missing instanceof Result.Err<ResourceManager.LocalResource> missingErr)) {
            throw new AssertionError("Expected Result.Err for missing resource");
        }
        assertThat(missingErr.diagnostic().code()).isEqualTo("RES_NOT_FOUND");

        Result<ResourceManager.LocalResource> forbidden = manager.resolveResult("https://evil.test/x.png");
        if (!(forbidden instanceof Result.Err<ResourceManager.LocalResource> forbiddenErr)) {
            throw new AssertionError("Expected Result.Err for forbidden remote URL");
        }
        assertThat(forbiddenErr.diagnostic().code()).isEqualTo("RES_FORBIDDEN_PATH");

        assertThat(manager.preload("ok.txt", "missing.png")).isEqualTo(1);
        assertThat(manager.invalidate("ok.txt")).isTrue();
    }
}
