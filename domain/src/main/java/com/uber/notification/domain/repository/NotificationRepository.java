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

    /** Filtered history: optional event type + created-at window (null = no constraint). */
    List<Notification> findHistoryForUser(UUID userId, boolean includeDeleted,
                                          com.uber.notification.domain.model.EventType eventType,
                                          Instant since, Instant before, int page, int size);

    long countUnread(UUID userId);

    /** Marks every unread, non-deleted notification of the user as read. Returns rows touched. */
    int markAllRead(UUID userId);

    List<Notification> findByStatus(NotificationStatus status, int limit);

    long countByStatus(NotificationStatus status);

    long countTotal();

    long countCreatedSince(Instant since);

    long countUnreadTotal();

    long countReadTotal();

    /** IDs of notifications created at or before {@code cutoff} (retention purge candidate). */
    List<UUID> findIdsCreatedBefore(Instant cutoff, int limit);

    /** Hard-delete by IDs (used by the retention job after the retention window). */
    void deleteByIds(List<UUID> ids);
}
