package dev.kairo.events;

import dev.kairo.core.KairoId;
import dev.kairo.lifecycle.Disposable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Platform-neutral UI event dispatcher implementing Capture -> Target -> Bubble propagation.
 */
public final class EventDispatcher {
    private record ListenerEntry<E extends UIEvent>(Class<E> eventType, Consumer<E> handler) {
        @SuppressWarnings("unchecked")
        void invokeIfMatching(UIEvent event) {
            if (eventType.isInstance(event)) {
                handler.accept((E) event);
            }
        }
    }

    private final Map<KairoId, List<ListenerEntry<?>>> captureListeners = new LinkedHashMap<>();
    private final Map<KairoId, List<ListenerEntry<?>>> bubbleListeners = new LinkedHashMap<>();

    public <E extends UIEvent> Disposable addCaptureListener(KairoId nodeId, Class<E> eventType, Consumer<E> handler) {
        ListenerEntry<E> entry = new ListenerEntry<>(eventType, handler);
        captureListeners.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(entry);
        return () -> removeEntry(captureListeners, nodeId, entry);
    }

    public <E extends UIEvent> Disposable addListener(KairoId nodeId, Class<E> eventType, Consumer<E> handler) {
        ListenerEntry<E> entry = new ListenerEntry<>(eventType, handler);
        bubbleListeners.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(entry);
        return () -> removeEntry(bubbleListeners, nodeId, entry);
    }

    public void clearListeners(KairoId nodeId) {
        captureListeners.remove(nodeId);
        bubbleListeners.remove(nodeId);
    }

    public int listenerCount(KairoId nodeId) {
        List<ListenerEntry<?>> cap = captureListeners.get(nodeId);
        List<ListenerEntry<?>> bub = bubbleListeners.get(nodeId);
        return (cap != null ? cap.size() : 0) + (bub != null ? bub.size() : 0);
    }

    public void dispatch(UIEvent event, List<KairoId> pathFromRootToTarget) {
        if (pathFromRootToTarget == null || pathFromRootToTarget.isEmpty()) {
            return;
        }
        int targetIdx = pathFromRootToTarget.size() - 1;

        // 1. Capture phase (root -> target parent)
        event.setPhase(UIEvent.Phase.CAPTURE);
        for (int i = 0; i < targetIdx && !event.isPropagationStopped(); i++) {
            KairoId current = pathFromRootToTarget.get(i);
            event.setCurrentTarget(current);
            invokeList(captureListeners.get(current), event);
        }

        // 2. Target phase
        if (!event.isPropagationStopped()) {
            event.setPhase(UIEvent.Phase.TARGET);
            KairoId target = pathFromRootToTarget.get(targetIdx);
            event.setCurrentTarget(target);
            invokeList(captureListeners.get(target), event);
            if (!event.isPropagationStopped()) {
                invokeList(bubbleListeners.get(target), event);
            }
        }

        // 3. Bubble phase (target parent -> root)
        event.setPhase(UIEvent.Phase.BUBBLE);
        for (int i = targetIdx - 1; i >= 0 && !event.isPropagationStopped(); i--) {
            KairoId current = pathFromRootToTarget.get(i);
            event.setCurrentTarget(current);
            invokeList(bubbleListeners.get(current), event);
        }
        event.setPhase(UIEvent.Phase.NONE);
    }

    private void invokeList(List<ListenerEntry<?>> entries, UIEvent event) {
        if (entries == null || entries.isEmpty()) return;
        for (ListenerEntry<?> entry : List.copyOf(entries)) {
            if (event.isPropagationStopped()) break;
            entry.invokeIfMatching(event);
        }
    }

    private void removeEntry(Map<KairoId, List<ListenerEntry<?>>> map, KairoId nodeId, ListenerEntry<?> entry) {
        List<ListenerEntry<?>> list = map.get(nodeId);
        if (list != null) {
            list.remove(entry);
            if (list.isEmpty()) {
                map.remove(nodeId);
            }
        }
    }
}
