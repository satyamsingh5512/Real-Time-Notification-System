package com.uber.notification.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTest {

    private Notification newNotification(int maxAttempts) {
        return new Notification(UUID.randomUUID(), UUID.randomUUID(), EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", Map.of(), maxAttempts, null, Instant.now(), "idem-1");
    }

    @Test
    void startsInPendingStatusWhenNotScheduled() {
        assertThat(newNotification(3).getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void startsInScheduledStatusWhenScheduledForIsSet() {
        Notification n = new Notification(UUID.randomUUID(), UUID.randomUUID(), EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", Map.of(), 3, Instant.now().plusSeconds(60),
                Instant.now(), "idem-2");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
    }

    @Test
    void markProcessingIncrementsAttemptCount() {
        Notification n = newNotification(3);
        n.markProcessing();
        assertThat(n.getAttemptCount()).isEqualTo(1);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PROCESSING);
    }

    @Test
    void markFailedTransitionsToRetryingWhenAttemptsRemain() {
        Notification n = newNotification(3);
        n.markProcessing();
        n.markFailed("boom");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        assertThat(n.canRetry()).isTrue();
    }

    @Test
    void markFailedTransitionsToDeadLetteredWhenAttemptsExhausted() {
        Notification n = newNotification(1);
        n.markProcessing(); // attemptCount = 1 = maxAttempts
        n.markFailed("boom");
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        assertThat(n.canRetry()).isFalse();
    }

    @Test
    void markReadIsIdempotent() {
        Notification n = newNotification(3);
        n.markRead();
        Instant firstReadAt = n.getReadAt();
        n.markRead();
        assertThat(n.getReadAt()).isEqualTo(firstReadAt);
        assertThat(n.isRead()).isTrue();
    }

    @Test
    void softDeleteSetsDeletedFlag() {
        Notification n = newNotification(3);
        n.softDelete();
        assertThat(n.isDeleted()).isTrue();
    }
}
