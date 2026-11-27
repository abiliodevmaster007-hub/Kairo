package dev.kairo.state;

/**
 * Global handler notified when a reactive listener throws during immediate notification
 * or transaction flush, allowing isolation without aborting sibling subscribers.
 */
@FunctionalInterface
public interface ReactiveErrorHandler {
    ReactiveErrorHandler PRINT_STDERR = (source, error) ->
            System.err.println("[Kairo Reactive Error] " + source + ": " + error);

    void onListenerError(String source, Throwable error);
}
