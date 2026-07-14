package com.uber.notification.common.exception;

public class ValidationException extends NotificationPlatformException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
