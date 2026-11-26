package dev.kairo.state;

import dev.kairo.lifecycle.Disposable;

/**
 * Common reactive source contract implemented by State, Derived, and ObservableList
 * for uniform dependency tracking and runtime subscription ownership.
 */
public interface ReactiveSource {
    Disposable subscribeAny(Runnable listener);
}
