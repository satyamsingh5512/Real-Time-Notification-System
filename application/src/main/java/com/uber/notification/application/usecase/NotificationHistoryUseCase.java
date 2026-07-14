package com.uber.notification.application.usecase;

import com.uber.notification.common.exception.ResourceNotFoundException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side use cases for a user's notification inbox: paginated history, unread count,
 * mark read/unread, and soft delete. Deletions are soft (a `deleted` flag) so history
 * remains available for audit/compliance and can be restored or purged later by a
 * retention job, rather than being lost immediately.
 */
public class NotificationHistoryUseCase {

    private final NotificationRepository notificationRepository;

    public NotificationHistoryUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getHistory(UUID userId, boolean includeDeleted, int page, int size) {
        return notificationRepository.findHistoryForUser(userId, includeDeleted, page, size);
    }

    public long getUnreadCount(UUID userId) {
        return notificationRepository.countUnread(userId);
    }

    public Notification markRead(UUID notificationId, UUID requestingUserId) {
        Notification notification = getOwned(notificationId, requestingUserId);
        notification.markRead();
        return notificationRepository.save(notification);
    }

    public Notification markUnread(UUID notificationId, UUID requestingUserId) {
        Notification notification = getOwned(notificationId, requestingUserId);
        notification.markUnread();
        return notificationRepository.save(notification);
    }

    public void softDelete(UUID notificationId, UUID requestingUserId) {
        Notification notification = getOwned(notificationId, requestingUserId);
        notification.softDelete();
        notificationRepository.save(notification);
    }

    public List<Notification> findDueForDelivery(int limit) {
        return notificationRepository.findDueForDelivery(Instant.now(), limit);
    }

    public List<Notification> findDeadLettered(int limit) {
        return notificationRepository.findByStatus(NotificationStatus.DEAD_LETTERED, limit);
    }

    private Notification getOwned(UUID notificationId, UUID requestingUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        if (!notification.getUserId().equals(requestingUserId)) {
            throw new ResourceNotFoundException("Notification not found: " + notificationId);
        }
        return notification;
    }
}
