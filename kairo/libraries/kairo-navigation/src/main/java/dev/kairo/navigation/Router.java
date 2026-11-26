package dev.kairo.navigation;

import dev.kairo.components.api.ComponentDescriptor;
import dev.kairo.components.api.ComponentDescriptor.ChildrenPolicy;
import dev.kairo.components.api.ComponentDescriptor.LayoutAxisHint;
import dev.kairo.components.api.ComponentDescriptor.SemanticRole;
import dev.kairo.components.api.ComponentRegistry;
import dev.kairo.state.Derived;
import dev.kairo.state.State;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Platform-independent Router with parameterized & wildcard routes, query params,
 * navigation guards with loop protection, back-stack management, and per-entry state retention.
 */
public final class Router {
    public record RouteDefinition(String pattern, String screenName) {}

    public record BackStackEntry(
            String path,
            String screenName,
            Map<String, String> params,
            Map<String, String> queryParams
    ) {
        public BackStackEntry(String path, String screenName, Map<String, String> params) {
            this(path, screenName, params, Map.of());
        }
    }

    public sealed interface GuardDecision {
        record Allow() implements GuardDecision {}
        record Redirect(String targetPath) implements GuardDecision {}
        record Block(String reason) implements GuardDecision {}
    }

    @FunctionalInterface
    public interface RouteGuard {
        GuardDecision evaluate(BackStackEntry destination);
    }

    private static final int MAX_REDIRECT_HOPS = 8;

    private final List<RouteDefinition> routes = new ArrayList<>();
    private final List<RouteGuard> guards = new ArrayList<>();
    private final Deque<BackStackEntry> stack = new ArrayDeque<>();
    private final Map<String, Map<String, Object>> entrySavedState = new LinkedHashMap<>();
    private final State<BackStackEntry> current = State.of(null);
    private final State<Integer> stackDepth = State.of(0);
    public final Derived<Boolean> canGoBack = Derived.derive(() -> stackDepth.get() > 1);
    private String lastBlockedReason;

    public void registerRoute(String pattern, String screenName) {
        routes.add(new RouteDefinition(pattern, screenName));
    }

    public void addGuard(RouteGuard guard) {
        if (guard != null) {
            guards.add(guard);
        }
    }

    public State<BackStackEntry> current() {
        return current;
    }

    public String lastBlockedReason() {
        return lastBlockedReason;
    }

    public List<BackStackEntry> backStack() {
        return Collections.unmodifiableList(new ArrayList<>(stack));
    }

    public boolean navigate(String path) {
        Optional<BackStackEntry> resolved = resolveWithGuards(path, 0);
        if (resolved.isEmpty()) return false;
        BackStackEntry entry = resolved.get();
        State.transaction(() -> {
            stack.push(entry);
            stackDepth.set(stack.size());
            current.set(entry);
        });
        return true;
    }

    public boolean replace(String path) {
        Optional<BackStackEntry> resolved = resolveWithGuards(path, 0);
        if (resolved.isEmpty()) return false;
        BackStackEntry entry = resolved.get();
        State.transaction(() -> {
            if (!stack.isEmpty()) {
                BackStackEntry popped = stack.pop();
                entrySavedState.remove(popped.path());
            }
            stack.push(entry);
            stackDepth.set(stack.size());
            current.set(entry);
        });
        return true;
    }

    public boolean navigateAndClearStack(String path) {
        Optional<BackStackEntry> resolved = resolveWithGuards(path, 0);
        if (resolved.isEmpty()) return false;
        BackStackEntry entry = resolved.get();
        State.transaction(() -> {
            stack.clear();
            entrySavedState.clear();
            stack.push(entry);
            stackDepth.set(stack.size());
            current.set(entry);
        });
        return true;
    }

    public boolean back() {
        if (stack.size() <= 1) return false;
        State.transaction(() -> {
            BackStackEntry popped = stack.pop();
            entrySavedState.remove(popped.path());
            stackDepth.set(stack.size());
            current.set(stack.peek());
        });
        return true;
    }

    public boolean popToRoot() {
        if (stack.size() <= 1) return false;
        State.transaction(() -> {
            while (stack.size() > 1) {
                BackStackEntry popped = stack.pop();
                entrySavedState.remove(popped.path());
            }
            stackDepth.set(stack.size());
            current.set(stack.peek());
        });
        return true;
    }

    public void saveCurrentEntryState(String key, Object value) {
        BackStackEntry cur = current.get();
        if (cur == null) return;
        entrySavedState.computeIfAbsent(cur.path(), ignored -> new LinkedHashMap<>()).put(key, value);
    }

    public Object readCurrentEntryState(String key) {
        BackStackEntry cur = current.get();
        if (cur == null) return null;
        Map<String, Object> state = entrySavedState.get(cur.path());
        return state != null ? state.get(key) : null;
    }

    private Optional<BackStackEntry> resolveWithGuards(String path, int hop) {
        if (hop > MAX_REDIRECT_HOPS) {
            this.lastBlockedReason = "Too many route redirects (" + MAX_REDIRECT_HOPS + ")";
            return Optional.empty();
        }
        Optional<BackStackEntry> match = match(path);
        if (match.isEmpty()) return Optional.empty();

        BackStackEntry candidate = match.get();
        for (RouteGuard guard : guards) {
            GuardDecision decision = guard.evaluate(candidate);
            if (decision instanceof GuardDecision.Redirect redirect) {
                return resolveWithGuards(redirect.targetPath(), hop + 1);
            }
            if (decision instanceof GuardDecision.Block block) {
                this.lastBlockedReason = block.reason();
                return Optional.empty();
            }
        }
        this.lastBlockedReason = null;
        return Optional.of(candidate);
    }

    private Optional<BackStackEntry> match(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) return Optional.empty();
        int qIdx = fullPath.indexOf('?');
        String cleanPath = qIdx >= 0 ? fullPath.substring(0, qIdx) : fullPath;
        Map<String, String> queryParams = qIdx >= 0 ? parseQuery(fullPath.substring(qIdx + 1)) : Map.of();

        String[] actualSegments = splitPath(cleanPath);
        RouteDefinition wildcard = null;

        for (RouteDefinition def : routes) {
            if ("*".equals(def.pattern().trim())) {
                wildcard = def;
                continue;
            }
            String[] patternSegments = splitPath(def.pattern());
            if (actualSegments.length != patternSegments.length) continue;

            Map<String, String> params = new LinkedHashMap<>();
            boolean matched = true;
            for (int i = 0; i < patternSegments.length; i++) {
                String p = patternSegments[i];
                String a = actualSegments[i];
                if (p.startsWith(":")) {
                    params.put(p.substring(1), a);
                } else if (!p.equals(a)) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return Optional.of(new BackStackEntry(
                        fullPath,
                        def.screenName(),
                        Collections.unmodifiableMap(params),
                        queryParams
                ));
            }
        }

        if (wildcard != null) {
            return Optional.of(new BackStackEntry(fullPath, wildcard.screenName(), Map.of(), queryParams));
        }
        return Optional.empty();
    }

    private static String[] splitPath(String path) {
        String trimmed = path.startsWith("/") ? path.substring(1) : path;
        if (trimmed.isEmpty()) return new String[]{""};
        return trimmed.split("/");
    }

    private static Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) return Map.of();
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                map.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            } else if (!pair.isBlank()) {
                map.put(pair.trim(), "true");
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public static void registerAll(ComponentRegistry registry) {
        registry.register(ComponentDescriptor.builder("Routes")
                .role(SemanticRole.NAVIGATION)
                .axis(LayoutAxisHint.OVERLAY)
                .children(ChildrenPolicy.MULTIPLE)
                .prop("initialPath", String.class, "/")
                .build());
        registry.register(ComponentDescriptor.builder("Route")
                .role(SemanticRole.NAVIGATION)
                .axis(LayoutAxisHint.LEAF)
                .children(ChildrenPolicy.NONE)
                .requiredProp("path", String.class)
                .requiredProp("screen", String.class)
                .build());
    }
}
