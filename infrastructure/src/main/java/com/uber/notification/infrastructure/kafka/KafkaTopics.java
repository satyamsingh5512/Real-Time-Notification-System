package com.uber.notification.infrastructure.kafka;

/**
 * Central registry of Kafka topic names. Keeping these as constants (rather than scattering
 * string literals across producers/consumers) avoids typo-driven bugs and makes the
 * retry/DLQ topic chain easy to reason about:
 *
 *   events.* (business events) -> notification.delivery -> notification.retry -> notification.dlq
 */
public final class KafkaTopics {

    public static final String ORDER_PLACED = "events.order.placed";
    public static final String ORDER_DELIVERED = "events.order.delivered";
    public static final String PAYMENT_SUCCESS = "events.payment.success";
    public static final String COMMENT_ADDED = "events.social.comment-added";
    public static final String LIKE_RECEIVED = "events.social.like-received";
    public static final String MENTIONED = "events.social.mentioned";
    public static final String PASSWORD_RESET = "events.auth.password-reset";
    public static final String OTP_GENERATED = "events.auth.otp-generated";

    /** A notification ready for immediate delivery attempt (produced by the fan-out consumer). */
    public static final String NOTIFICATION_DELIVERY = "notification.delivery";

    /** A notification whose delivery failed transiently and should be retried after a delay. */
    public static final String NOTIFICATION_RETRY = "notification.retry";

    /** A notification that exhausted all retry attempts or failed permanently. */
    public static final String NOTIFICATION_DLQ = "notification.dlq";

    private KafkaTopics() {
    }
}
