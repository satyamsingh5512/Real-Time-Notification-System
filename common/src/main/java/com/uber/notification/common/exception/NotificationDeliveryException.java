package com.uber.notification.common.exception;

/**
 * Thrown by a provider adapter (Email/SMS/Push) when delivery fails transiently
 * and the caller should retry (with backoff) or route to the DLQ after exhausting attempts.
 */
public class NotificationDeliveryException extends NotificationPlatformException {

    private final boolean retryable;

    public NotificationDeliveryException(String message, boolean retryable, Throwable cause) {
        super("DELIVERY_FAILED", message, cause);
        this.retryable = retryable;
    }

    public NotificationDeliveryException(String message, boolean retryable) {
        super("DELIVERY_FAILED", message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
