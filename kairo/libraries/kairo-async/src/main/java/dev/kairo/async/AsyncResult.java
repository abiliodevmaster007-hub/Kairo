package dev.kairo.async;

/**
 * Exhaustive Java 17 snapshot representation of a FutureState operation.
 */
public sealed interface AsyncResult<T> {
    record Idle<T>() implements AsyncResult<T> {}
    record Loading<T>() implements AsyncResult<T> {}
    record Success<T>(T value) implements AsyncResult<T> {}
    record Failure<T>(Throwable error) implements AsyncResult<T> {}
    record Cancelled<T>() implements AsyncResult<T> {}
}
