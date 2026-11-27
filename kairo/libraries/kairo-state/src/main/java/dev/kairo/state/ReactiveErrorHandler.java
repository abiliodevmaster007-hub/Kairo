package dev.kairo.state;

/**
 * Global handler notified when a reactive listener, Derived computation, or transaction
 * encounters a runtime failure, enabling isolation without aborting sibling subscribers.
 */
@FunctionalInterface
public interface ReactiveErrorHandler {
    ReactiveErrorHandler PRINT_STDERR = (source, error) ->
            System.err.println("[Kairo Reactive Error] " + source + ": " + error);

    void onListenerError(String source, Throwable error);

    default void onDerivedError(Derived<?> derived, Throwable error) {
        onListenerError("Derived.recompute", error);
    }

    default void onTransactionError(Throwable error) {
        onListenerError("State.transaction", error);
    }
}
