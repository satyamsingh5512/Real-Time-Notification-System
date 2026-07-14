package com.uber.notification.application.usecase;

import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.application.template.EventChannelRouting;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Use case #1 in the pipeline: takes a raw {@link DomainEvent} consumed from Kafka and
 * fans it out into one {@link Notification} row per eligible channel, after applying:
 *   1. The static event->channel routing table (product policy)
 *   2. Per-user channel opt-in/opt-out preferences
 *   3. Quiet hours suppression (channel is skipped, except channels marked urgent-only bypass)
 *   4. Idempotency (duplicate Kafka deliveries must not create duplicate notifications)
 *
 * Persisting is the only side effect here; actual delivery is handled asynchronously by
 * {@link DeliverNotificationUseCase} so that a slow provider never blocks the consumer thread.
 */
public class ProcessIncomingEventUseCase {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final NotificationRepository notificationRepository;
    private final UserPreferenceRepository preferenceRepository;

    public ProcessIncomingEventUseCase(NotificationRepository notificationRepository,
                                        UserPreferenceRepository preferenceRepository) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
    }

    public List<Notification> execute(DomainEvent event) {
        List<NotificationChannel> candidateChannels = EventChannelRouting.channelsFor(event.eventType());
        Optional<UserPreference> preference =
                preferenceRepository.findByUserIdAndEventType(event.userId(), event.eventType());

        int currentHour = Instant.now().atZone(ZoneOffset.UTC).getHour();

        return candidateChannels.stream()
                .filter(channel -> isChannelAllowed(channel, preference, currentHour))
                .map(channel -> createIfNotDuplicate(event, channel))
                .flatMap(Optional::stream)
                .toList();
    }

    private boolean isChannelAllowed(NotificationChannel channel, Optional<UserPreference> preference, int currentHour) {
        boolean enabled = preference.map(p -> p.isChannelEnabled(channel)).orElse(true);
        if (!enabled) {
            return false;
        }
        boolean inQuietHours = preference.map(p -> p.isWithinQuietHours(currentHour)).orElse(false);
        // Critical security/account channels bypass quiet hours (OTP, password reset).
        boolean bypassesQuietHours = channel == NotificationChannel.SMS || channel == NotificationChannel.EMAIL;
        return !inQuietHours || bypassesQuietHours && isSecurityCritical(preference);
    }

    private boolean isSecurityCritical(Optional<UserPreference> preference) {
        return preference.map(p -> p.getEventType() == com.uber.notification.domain.model.EventType.OTP_GENERATED
                || p.getEventType() == com.uber.notification.domain.model.EventType.PASSWORD_RESET).orElse(true);
    }

    private Optional<Notification> createIfNotDuplicate(DomainEvent event, NotificationChannel channel) {
        String idempotencyKey = event.eventId() + ":" + channel;
        if (notificationRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return Optional.empty();
        }
        Notification notification = new Notification(
                IdGenerator.newId(),
                event.userId(),
                event.eventType(),
                channel,
                event.eventType().name(),
                event.attributes(),
                DEFAULT_MAX_ATTEMPTS,
                null,
                Instant.now(),
                idempotencyKey
        );
        return Optional.of(notificationRepository.save(notification));
    }
}
