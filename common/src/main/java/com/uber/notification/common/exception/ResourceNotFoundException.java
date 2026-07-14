package com.uber.notification.common.exception;

public class ResourceNotFoundException extends NotificationPlatformException {
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}
