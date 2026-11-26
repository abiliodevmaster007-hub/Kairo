package dev.kairo.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Neutral resource path sanitizer & MIME inference.
 * Rejects path traversal ("..", encoded sequences), remote URLs, non-local URI schemes,
 * Windows drive letters, and control characters so Kairo v1 stays strictly local-only.
 */
public final class ResourcePathValidator {
    private ResourcePathValidator() {}

    public static String validateAndNormalize(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Resource path cannot be blank");
        }
        String trimmed = rawPath.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw new SecurityException("Control characters are forbidden in resource path");
            }
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c") || lower.contains("%00")) {
            throw new SecurityException("Percent-encoded path obfuscation is forbidden in Kairo v1: " + rawPath);
        }

        String unified = trimmed.replace('\\', '/');
        if (unified.indexOf(':') >= 0) {
            throw new SecurityException("Remote URLs, external schemes, and drive prefixes are forbidden in Kairo v1: " + rawPath);
        }

        String[] rawSegments = unified.split("/", -1);
        List<String> cleanSegments = new ArrayList<>(rawSegments.length);
        for (String seg : rawSegments) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg) || seg.contains("..")) {
                throw new SecurityException("Path traversal is forbidden in Kairo v1: " + rawPath);
            }
            cleanSegments.add(seg);
        }

        if (cleanSegments.isEmpty()) {
            throw new IllegalArgumentException("Resource path resolves to an empty path: " + rawPath);
        }
        return String.join("/", cleanSegments);
    }

    public static String inferMimeType(String normalizedPath) {
        if (normalizedPath == null) {
            return "application/octet-stream";
        }
        int dot = normalizedPath.lastIndexOf('.');
        if (dot < 0 || dot == normalizedPath.length() - 1) {
            return "application/octet-stream";
        }
        String ext = normalizedPath.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            case "xml" -> "application/xml";
            case "kcss", "css" -> "text/kairo-style";
            case "json" -> "application/json";
            case "txt", "md" -> "text/plain";
            case "ttf" -> "font/ttf";
            case "otf" -> "font/otf";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }
}
