package com.uber.notification.common.result;

import java.util.function.Function;

/**
 * Lightweight Result type for operations where exceptions are too heavy
 * (e.g. per-item provider send results in a batch). Not a replacement for
 * domain exceptions, which are still used for control flow across layers.
 */
public sealed interface Result<T> {

    record Success<T>(T value) implements Result<T> {
    }

    record Failure<T>(String errorCode, String message, Throwable cause) implements Result<T> {
    }

    static <T> Result<T> success(T value) {
        return new Success<>(value);
    }

    static <T> Result<T> failure(String errorCode, String message) {
        return new Failure<>(errorCode, message, null);
    }

    static <T> Result<T> failure(String errorCode, String message, Throwable cause) {
        return new Failure<>(errorCode, message, cause);
    }

    default boolean isSuccess() {
        return this instanceof Success<T>;
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        if (this instanceof Success<T> s) {
            return new Success<>(mapper.apply(s.value()));
        }
        Failure<T> f = (Failure<T>) this;
        return new Failure<>(f.errorCode(), f.message(), f.cause());
    }
}
