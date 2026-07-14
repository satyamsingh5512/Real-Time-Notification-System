package com.uber.notification.domain.repository;

import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Output port for notification persistence + history queries. */
public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(UUID id);

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    /** Notifications due now: PENDING, or SCHEDULED with scheduledFor <= now, or RETRYING. */
    List<Notification> findDueForDelivery(Instant now, int limit);

    List<Notification> findHistoryForUser(UUID userId, boolean includeDeleted, int page, int size);

    long countUnread(UUID userId);

    List<Notification> findByStatus(NotificationStatus status, int limit);
}
