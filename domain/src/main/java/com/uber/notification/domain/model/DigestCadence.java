package com.uber.notification.domain.model;

/**
 * Digest batching cadence for low-value email (SuprSend/Slack pattern):
 * {@code OFF} preserves the pre-digest immediate behavior.
 */
public enum DigestCadence {
    OFF,
    DAILY,
    WEEKLY
}
