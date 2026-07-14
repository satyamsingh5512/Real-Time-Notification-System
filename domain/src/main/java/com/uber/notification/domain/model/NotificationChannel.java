package com.uber.notification.domain.model;

/** Delivery channel for a notification. Maps 1:1 to a NotificationProvider strategy implementation. */
public enum NotificationChannel {
    EMAIL,
    SMS,
    PUSH,
    WEBSOCKET,
    IN_APP
}
