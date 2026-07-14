package com.uber.notification.application.port;

import com.uber.notification.domain.model.Notification;

/**
 * Output port for publishing a notification onto the retry-with-backoff topic or the
 * dead-letter topic. The Kafka adapter implementation schedules the message on a
 * per-attempt delay topic (e.g. retry-1m, retry-5m, retry-30m) chosen from the computed
 * backoff duration, or routes straight to the DLQ topic once attempts are exhausted.
 */
public interface RetryPublisherPort {

    void publishForRetry(Notification notification, java.time.Duration delay);

    void publishToDeadLetter(Notification notification, String reason);
}
