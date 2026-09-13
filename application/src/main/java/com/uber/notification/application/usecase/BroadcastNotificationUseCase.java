package com.uber.notification.application.usecase;

import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationPriority;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin broadcast: persists one IN_APP notification per registered user and hands
 * each to the delivery use case immediately. Users are scanned in pages so memory
 * stays bounded; each row carries a broadcast-scoped idempotency key.
 */
public class BroadcastNotificationUseCase {

    private static final int PAGE_SIZE = 200;
    private static final int MAX_ATTEMPTS = 3;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final DeliverNotificationUseCase deliverNotificationUseCase;

    public BroadcastNotificationUseCase(NotificationRepository notificationRepository,
                                        UserRepository userRepository,
                                        DeliverNotificationUseCase deliverNotificationUseCase) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.deliverNotificationUseCase = deliverNotificationUseCase;
    }

    public int broadcast(String title, String message, NotificationPriority priority) {
        String broadcastId = IdGenerator.newId().toString();
        int delivered = 0;
        int page = 0;
        while (true) {
            List<UUID> userIds = userRepository.findAllIds(page, PAGE_SIZE);
            if (userIds.isEmpty()) {
                break;
            }
            for (UUID userId : userIds) {
                Notification notification = new Notification(
                        IdGenerator.newId(), userId, EventType.BROADCAST_ANNOUNCEMENT,
                        NotificationChannel.IN_APP, "BROADCAST",
                        Map.of("title", title, "message", message),
                        MAX_ATTEMPTS, null, Instant.now(),
                        "broadcast:" + broadcastId + ":" + userId);
                notification.setPriority(priority);
                notification.markRendered(title, message);
                notificationRepository.save(notification);
                try {
                    deliverNotificationUseCase.execute(notification);
                } catch (Exception ignored) {
                    // Delivery failures are recorded on the row (FAILED/RETRYING/DEAD_LETTERED)
                    // by the delivery use case itself; keep fanning out to remaining users.
                }
                delivered++;
            }
            if (userIds.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return delivered;
    }
}
