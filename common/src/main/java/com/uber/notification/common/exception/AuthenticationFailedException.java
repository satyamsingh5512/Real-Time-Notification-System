package com.uber.notification.common.exception;

/** Thrown when a login/token operation fails. */
public class AuthenticationFailedException extends NotificationPlatformException {
    public AuthenticationFailedException(String message) {
        super("AUTH_FAILED", message);
    }
}
