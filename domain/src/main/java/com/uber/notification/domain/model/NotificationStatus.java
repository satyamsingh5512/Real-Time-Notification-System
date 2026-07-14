package com.uber.notification.domain.model;

/** Lifecycle state of a single notification delivery attempt record. */
public enum NotificationStatus {
    PENDING,
    SCHEDULED,
    PROCESSING,
    SENT,
    DELIVERED,
    FAILED,
    RETRYING,
    DEAD_LETTERED,
    CANCELLED
}
