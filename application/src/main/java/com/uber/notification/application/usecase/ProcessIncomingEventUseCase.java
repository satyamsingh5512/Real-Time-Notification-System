package com.uber.notification.application.usecase;

import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.application.policy.DeliveryPolicy;
import com.uber.notification.application.policy.FrequencyCapPolicy;
import com.uber.notification.application.template.EventChannelRouting;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationIntent;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use case #1 in the pipeline: takes a raw {@link DomainEvent} consumed from Kafka and
 * fans it out into one {@link Notification} row per eligible channel, after applying:
 *   1. The static event->channel routing table (product policy)
 *   2. Notification <em>intent</em> (ALL / MENTIONS / MUTE) — whether to notify at all
 *   3. Per-user channel opt-in/opt-out preferences + push delivery switch
 *   4. Quiet hours suppression (channel is skipped, except security-critical bypass)
 *   5. Push frequency caps (LinkedIn ATC style) — over-budget pushes are
 *      <em>deferred</em> (SCHEDULED), never dropped
 *   6. Digest batching (Slack/SuprSend style) — low-value email becomes QUEUED_DIGEST
 *   7. Idempotency (duplicate Kafka deliveries must not create duplicate notifications)
 *
 * Persisting is the only side effect here; actual delivery is handled asynchronously by
 * {@link DeliverNotificationUseCase} so that a slow provider never blocks the consumer thread.
 */
public class ProcessIncomingEventUseCase {

    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final NotificationRepository notificationRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final FrequencyCapPolicy frequencyCapPolicy;

    public ProcessIncomingEventUseCase(NotificationRepository notificationRepository,
                                        UserPreferenceRepository preferenceRepository) {
        this(notificationRepository, preferenceRepository,
                new FrequencyCapPolicy(notificationRepository));
    }

    public ProcessIncomingEventUseCase(NotificationRepository notificationRepository,
                                        UserPreferenceRepository preferenceRepository,
                                        FrequencyCapPolicy frequencyCapPolicy) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.frequencyCapPolicy = frequencyCapPolicy;
    }

    public List<Notification> execute(DomainEvent event) {
        List<NotificationChannel> candidateChannels = EventChannelRouting.channelsFor(event.eventType());
        Optional<UserPreference> preference =
                preferenceRepository.findByUserIdAndEventType(event.userId(), event.eventType());

        if (isMutedByIntent(event.eventType(), preference)) {
            return List.of();
        }

        int currentHour = Instant.now().atZone(ZoneOffset.UTC).getHour();
        Instant now = Instant.now();
        List<Notification> created = new ArrayList<>();
        for (NotificationChannel channel : candidateChannels) {
            if (!isChannelAllowed(channel, event.eventType(), preference, currentHour)) {
                continue;
            }
            // ATC caps: defer over-budget pushes instead of dropping them.
            if (frequencyCapPolicy != null && channel == NotificationChannel.PUSH) {
                FrequencyCapPolicy.Decision decision = frequencyCapPolicy.check(
                        event.userId(), channel, preference.orElse(null), now);
                if (decision.verdict() == FrequencyCapPolicy.Verdict.DEFER) {
                    createDeferred(event, channel, decision.deliverAt()).ifPresent(created::add);
                    continue;
                }
            }
            // Digest: batch low-value email, keep in-app realtime.
            if (DeliveryPolicy.shouldDigest(channel, com.uber.notification.domain.model.NotificationPriority.MEDIUM,
                    event.eventType(), preference.orElse(null))) {
                createDigest(event, channel).ifPresent(created::add);
                continue;
            }
            createIfNotDuplicate(event, channel, null).ifPresent(created::add);
        }
        return List.copyOf(created);
    }

    private boolean isMutedByIntent(EventType eventType, Optional<UserPreference> preference) {
        if (preference.isEmpty() || isSecurityCriticalEvent(eventType)) {
            return false;
        }
        NotificationIntent intent = preference.get().getIntent();
        return switch (intent) {
            case MUTE -> true;
            case MENTIONS -> !isDirectlyAddressed(eventType);
            case ALL -> false;
        };
    }

    private boolean isDirectlyAddressed(EventType eventType) {
        return eventType == EventType.MENTIONED
                || eventType == EventType.COMMENT_ADDED
                || isSecurityCriticalEvent(eventType);
    }

    private boolean isSecurityCriticalEvent(EventType eventType) {
        return eventType == EventType.OTP_GENERATED || eventType == EventType.PASSWORD_RESET;
    }

    private boolean isChannelAllowed(NotificationChannel channel, EventType eventType,
                                     Optional<UserPreference> preference, int currentHour) {
        if (preference.isPresent()) {
            UserPreference p = preference.get();
            if (channel == NotificationChannel.PUSH && !p.isPushEnabled()) {
                return false;
            }
        }
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

    private Optional<Notification> createDeferred(DomainEvent event, NotificationChannel channel, Instant deliverAt) {
        return createIfNotDuplicate(event, channel, deliverAt);
    }

    private Optional<Notification> createDigest(DomainEvent event, NotificationChannel channel) {
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
        notification.markQueuedForDigest();
        return Optional.of(notificationRepository.save(notification));
    }

    private Optional<Notification> createIfNotDuplicate(DomainEvent event, NotificationChannel channel,
                                                        Instant scheduledFor) {
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
                scheduledFor,
                Instant.now(),
                idempotencyKey
        );
        return Optional.of(notificationRepository.save(notification));
    }
}
