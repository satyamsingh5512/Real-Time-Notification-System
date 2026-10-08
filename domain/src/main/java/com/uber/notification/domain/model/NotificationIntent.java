package com.uber.notification.domain.model;

/**
 * What activity should generate a notification at all (Slack 2026 model):
 * intent is separate from <em>how</em> it is delivered (channel toggles + push switch).
 */
public enum NotificationIntent {
    /** Notify for every event of this type (previous default behavior). */
    ALL,
    /** Only notify for directly-addressed activity (mentions, replies, OTPs, password resets). */
    MENTIONS,
    /** Never generate notifications for this event type (critical security events still pass). */
    MUTE
}
