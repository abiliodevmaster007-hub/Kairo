package dev.kairo.core;

import java.util.Objects;
import java.util.function.Function;

/**
 * Neutral functional Result type for explicit error handling without platform exceptions.
 */
public sealed interface Result<T> {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(Diagnostic diagnostic) implements Result<T> {
        public Err {
            Objects.requireNonNull(diagnostic, "Diagnostic cannot be null");
        }
    }

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> error(String code, String message, SourceLocation location) {
        return new Err<>(Diagnostic.error(code, message, location));
    }

    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        if (this instanceof Ok<T> ok) {
            return new Ok<>(mapper.apply(ok.value()));
        }
        return new Err<>(((Err<T>) this).diagnostic());
    }
}
