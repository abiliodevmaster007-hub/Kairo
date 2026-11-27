package dev.kairo.state;

/**
 * Defines how State, ObservableList, and transaction mutations behave when invoked off the UI thread.
 */
public enum ConcurrencyPolicy {
    /**
     * Automatically marshals background mutations onto the configured UiScheduler.
     */
    DISPATCH_TO_UI,

    /**
     * Throws IllegalStateException immediately if mutated off the UI thread.
     */
    STRICT_THROW,

    /**
     * Executes mutations directly on the calling thread without thread-affinity checks.
     */
    IMMEDIATE
}
