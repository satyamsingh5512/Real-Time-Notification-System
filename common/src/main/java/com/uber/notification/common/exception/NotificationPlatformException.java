package com.uber.notification.common.exception;

/**
 * Base type for all business/domain exceptions in the platform.
 * Kept framework-agnostic so the domain and application layers never depend on Spring.
 */
public abstract class NotificationPlatformException extends RuntimeException {

    private final String errorCode;

    protected NotificationPlatformException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected NotificationPlatformException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
