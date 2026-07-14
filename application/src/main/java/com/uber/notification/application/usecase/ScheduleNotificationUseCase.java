package com.uber.notification.application.usecase;

import com.uber.notification.common.exception.ValidationException;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.repository.NotificationRepository;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Use case for explicitly scheduled / delayed notifications (as opposed to the immediate
 * fan-out path in {@link ProcessIncomingEventUseCase}). A background poller
 * ({@code NotificationSchedulerJob} in infrastructure) periodically calls
 * {@link NotificationHistoryUseCase#findDueForDelivery} to pick these up once
 * {@code scheduledFor} has elapsed and hand them to {@link DeliverNotificationUseCase}.
 */
public class ScheduleNotificationUseCase {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final NotificationRepository notificationRepository;

    public ScheduleNotificationUseCase(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification schedule(UUID userId, EventType eventType, NotificationChannel channel,
                                  String templateCode, Map<String, String> payload, Instant scheduledFor) {
        if (scheduledFor == null || scheduledFor.isBefore(Instant.now())) {
            throw new ValidationException("scheduledFor must be a future timestamp");
        }
        String idempotencyKey = IdGenerator.newIdempotencyKey("scheduled");
        Notification notification = new Notification(
                IdGenerator.newId(), userId, eventType, channel, templateCode, payload,
                DEFAULT_MAX_ATTEMPTS, scheduledFor, Instant.now(), idempotencyKey
        );
        return notificationRepository.save(notification);
    }
}
