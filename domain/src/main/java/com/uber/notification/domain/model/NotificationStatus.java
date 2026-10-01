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
    CANCELLED,
    /**
     * Batched for digest delivery: a low-value email suppressed into the user's
     * pending digest. Flushed as one grouped email by the digest job; in-app
     * delivery for the same event still happens immediately.
     */
    QUEUED_DIGEST
}
