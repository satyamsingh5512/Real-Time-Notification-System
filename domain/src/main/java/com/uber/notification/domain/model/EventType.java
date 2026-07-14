package com.uber.notification.domain.model;

/** Domain event types produced by upstream services and consumed from Kafka. */
public enum EventType {
    ORDER_PLACED,
    ORDER_DELIVERED,
    PAYMENT_SUCCESS,
    COMMENT_ADDED,
    LIKE_RECEIVED,
    MENTIONED,
    PASSWORD_RESET,
    OTP_GENERATED
}
