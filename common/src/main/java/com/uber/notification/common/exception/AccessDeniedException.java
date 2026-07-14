package com.uber.notification.common.exception;

/** Thrown when an authenticated principal lacks the required role/permission. */
public class AccessDeniedException extends NotificationPlatformException {
    public AccessDeniedException(String message) {
        super("ACCESS_DENIED", message);
    }
}
