package com.uber.notification.application.usecase;

import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.application.policy.DeliveryPolicy;
import com.uber.notification.application.policy.FrequencyCapPolicy;
import com.uber.notification.application.template.TemplateRolloutSelector;
import com.uber.notification.domain.model.*;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Lenient: several tests below exercise pure policies (digest flush, template rollout)
// that never touch the fan-out mocks stubbed in setUp.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationOptimizationPolicyTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserPreferenceRepository preferenceRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;

    private ProcessIncomingEventUseCase processUseCase;

    @BeforeEach
    void setUp() {
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(notificationRepository.countPushesSentSince(any(), any())).thenReturn(0L);
        processUseCase = new ProcessIncomingEventUseCase(notificationRepository, preferenceRepository,
                new FrequencyCapPolicy(notificationRepository));
    }

    // ---------- Intent vs delivery (Slack model) ----------

    @Test
    void muteIntentSuppressesAllChannels() {
        UUID userId = UUID.randomUUID();
        UserPreference muted = preferenceWith(userId, EventType.LIKE_RECEIVED, NotificationIntent.MUTE);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.LIKE_RECEIVED))
                .thenReturn(Optional.of(muted));

        List<Notification> result = processUseCase.execute(event("evt-mute", EventType.LIKE_RECEIVED, userId));

        assertThat(result).isEmpty();
    }

    @Test
    void mentionsIntentAllowsDirectlyAddressedEvents() {
        UUID userId = UUID.randomUUID();
        UserPreference mentions = preferenceWith(userId, EventType.MENTIONED, NotificationIntent.MENTIONS);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.MENTIONED))
                .thenReturn(Optional.of(mentions));

        List<Notification> result = processUseCase.execute(event("evt-mention", EventType.MENTIONED, userId));

        assertThat(result).isNotEmpty();
    }

    @Test
    void mentionsIntentBlocksAmbientEvents() {
        UUID userId = UUID.randomUUID();
        UserPreference mentions = preferenceWith(userId, EventType.LIKE_RECEIVED, NotificationIntent.MENTIONS);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.LIKE_RECEIVED))
                .thenReturn(Optional.of(mentions));

        List<Notification> result = processUseCase.execute(event("evt-like", EventType.LIKE_RECEIVED, userId));

        assertThat(result).isEmpty();
    }

    @Test
    void criticalSecurityEventsIgnoreMuteIntent() {
        UUID userId = UUID.randomUUID();
        UserPreference muted = preferenceWith(userId, EventType.OTP_GENERATED, NotificationIntent.MUTE);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.OTP_GENERATED))
                .thenReturn(Optional.of(muted));

        List<Notification> result = processUseCase.execute(event("evt-otp", EventType.OTP_GENERATED, userId));

        assertThat(result).isNotEmpty();
    }

    // ---------- ATC frequency caps ----------

    @Test
    void pushOverDailyBudgetIsDeferredNotDropped() {
        UUID userId = UUID.randomUUID();
        UserPreference pref = preferenceWith(userId, EventType.MENTIONED, NotificationIntent.ALL);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.MENTIONED))
                .thenReturn(Optional.of(pref));
        when(notificationRepository.countPushesSentSince(eq(userId), any()))
                .thenReturn(9L); // well over the default budget of 2

        List<Notification> result = processUseCase.execute(event("evt-cap", EventType.MENTIONED, userId));

        Notification push = result.stream()
                .filter(n -> n.getChannel() == NotificationChannel.PUSH)
                .findFirst().orElseThrow();
        assertThat(push.getScheduledFor()).isNotNull();          // deferred, not lost
        assertThat(push.getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
    }

    @Test
    void nonPushChannelsAreNeverFrequencyCapped() {
        UUID userId = UUID.randomUUID();
        UserPreference pref = preferenceWith(userId, EventType.MENTIONED, NotificationIntent.ALL);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.MENTIONED))
                .thenReturn(Optional.of(pref));
        when(notificationRepository.countPushesSentSince(any(), any())).thenReturn(99L);

        List<Notification> result = processUseCase.execute(event("evt-nocap", EventType.MENTIONED, userId));

        // Only PUSH is subject to caps; IN_APP/WEBSOCKET stay immediate even at 99 sends.
        assertThat(result).filteredOn(n -> n.getChannel() != NotificationChannel.PUSH)
                .isNotEmpty()
                .allMatch(n -> n.getScheduledFor() == null);
    }

    @Test
    void pushDisabledSuppressesPushChannelEntirely() {
        UUID userId = UUID.randomUUID();
        UserPreference pref = preferenceWith(userId, EventType.MENTIONED, NotificationIntent.ALL);
        pref.setPushEnabled(false);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.MENTIONED))
                .thenReturn(Optional.of(pref));

        List<Notification> result = processUseCase.execute(event("evt-nopush", EventType.MENTIONED, userId));

        assertThat(result).noneMatch(n -> n.getChannel() == NotificationChannel.PUSH);
    }

    // ---------- Digest batching ----------

    @Test
    void emailIsBatchedWhenUserOptsIntoDigest() {
        UUID userId = UUID.randomUUID();
        UserPreference pref = preferenceWith(userId, EventType.ORDER_PLACED, NotificationIntent.ALL);
        pref.setDigestCadence(DigestCadence.DAILY);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.ORDER_PLACED))
                .thenReturn(Optional.of(pref));

        List<Notification> result = processUseCase.execute(event("evt-digest", EventType.ORDER_PLACED, userId));

        Notification email = result.stream()
                .filter(n -> n.getChannel() == NotificationChannel.EMAIL)
                .findFirst().orElseThrow();
        assertThat(email.getStatus()).isEqualTo(NotificationStatus.QUEUED_DIGEST);
        // in-app must still be immediate — digests only batch email
        assertThat(result).anyMatch(n -> n.getChannel() == NotificationChannel.IN_APP
                && n.getStatus() != NotificationStatus.QUEUED_DIGEST);
    }

    @Test
    void otpEmailIsNeverDigested() {
        UUID userId = UUID.randomUUID();
        UserPreference pref = preferenceWith(userId, EventType.OTP_GENERATED, NotificationIntent.ALL);
        pref.setDigestCadence(DigestCadence.DAILY);
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.OTP_GENERATED))
                .thenReturn(Optional.of(pref));

        List<Notification> result = processUseCase.execute(event("evt-otp2", EventType.OTP_GENERATED, userId));

        assertThat(result).noneMatch(n -> n.getStatus() == NotificationStatus.QUEUED_DIGEST);
    }

    @Test
    void deliveryPolicyOnlyDigestsEmailBelowHighPriority() {
        UserPreference pref = preferenceWith(UUID.randomUUID(), EventType.ORDER_PLACED, NotificationIntent.ALL);
        pref.setDigestCadence(DigestCadence.DAILY);

        assertThat(DeliveryPolicy.shouldDigest(NotificationChannel.EMAIL, NotificationPriority.LOW,
                EventType.ORDER_PLACED, pref)).isTrue();
        assertThat(DeliveryPolicy.shouldDigest(NotificationChannel.EMAIL, NotificationPriority.HIGH,
                EventType.ORDER_PLACED, pref)).isFalse();
        assertThat(DeliveryPolicy.shouldDigest(NotificationChannel.PUSH, NotificationPriority.LOW,
                EventType.ORDER_PLACED, pref)).isFalse();
    }

    // ---------- Digest flush ----------

    @Test
    void digestGroupsItemsPerUserAndMarksFlushed() {
        UUID userId = UUID.randomUUID();
        Notification first = notification(userId, "Subject one", "Body one");
        Notification second = notification(userId, "Subject two", "Body two");
        first.markQueuedForDigest();
        second.markQueuedForDigest();
        when(notificationRepository.findByStatus(NotificationStatus.QUEUED_DIGEST, 500))
                .thenReturn(List.of(first, second));

        DigestFlushUseCase flush = new DigestFlushUseCase(notificationRepository);
        List<DigestFlushUseCase.Digest> digests = flush.collectPending();

        assertThat(digests).hasSize(1);
        DigestFlushUseCase.Digest digest = digests.get(0);
        assertThat(digest.userId()).isEqualTo(userId);
        assertThat(digest.items()).hasSize(2);
        assertThat(digest.subject()).contains("2");
        assertThat(digest.body()).contains("Subject one").contains("Subject two");

        flush.markFlushed(digest);
        assertThat(first.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(second.getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void singleItemDigestReusesItsOwnSubject() {
        UUID userId = UUID.randomUUID();
        Notification only = notification(userId, "Just this one", "body");
        only.markQueuedForDigest();
        when(notificationRepository.findByStatus(NotificationStatus.QUEUED_DIGEST, 500))
                .thenReturn(List.of(only));

        DigestFlushUseCase.Digest digest = new DigestFlushUseCase(notificationRepository)
                .collectPending().get(0);

        assertThat(digest.subject()).isEqualTo("Just this one");
    }

    // ---------- Template gradual rollout ----------

    @Test
    void fullTrafficServesNewVersionForEveryone() {
        NotificationTemplate v1 = template("ORDER_PLACED", 1, 100);
        NotificationTemplate v2 = template("ORDER_PLACED", 2, 100);
        when(templateRepository.findAllVersionsByCode("ORDER_PLACED")).thenReturn(List.of(v1, v2));

        TemplateRolloutSelector selector = new TemplateRolloutSelector(templateRepository);
        UUID userId = UUID.randomUUID();

        assertThat(selector.select("ORDER_PLACED", NotificationChannel.EMAIL, "en-US", userId))
                .contains(v2);
    }

    @Test
    void partialRolloutServesPreviousVersionOutsideTheBucket() {
        NotificationTemplate v1 = template("ORDER_PLACED", 1, 100);
        NotificationTemplate v2 = template("ORDER_PLACED", 2, 0); // 0% → nobody yet
        when(templateRepository.findAllVersionsByCode("ORDER_PLACED")).thenReturn(List.of(v1, v2));

        TemplateRolloutSelector selector = new TemplateRolloutSelector(templateRepository);
        UUID userId = UUID.randomUUID();

        assertThat(selector.select("ORDER_PLACED", NotificationChannel.EMAIL, "en-US", userId))
                .contains(v1);
    }

    @Test
    void rolloutBucketIsStableForTheSameUser() {
        UUID userId = UUID.randomUUID();
        assertThat(NotificationTemplate.bucketFor(userId, "ORDER_PLACED"))
                .isEqualTo(NotificationTemplate.bucketFor(userId, "ORDER_PLACED"));
    }

    // ---------- helpers ----------

    private DomainEvent event(String eventId, EventType type, UUID userId) {
        return new DomainEvent(eventId, type, userId, Map.of("actorName", "Someone"), Instant.now());
    }

    private UserPreference preferenceWith(UUID userId, EventType type, NotificationIntent intent) {
        return new UserPreference(UUID.randomUUID(), userId, type,
                new EnumMap<>(NotificationChannel.class), false, 0, 0, Instant.now(),
                intent, true, DigestCadence.OFF, 2, 8);
    }

    private Notification notification(UUID userId, String subject, String body) {
        Notification n = new Notification(UUID.randomUUID(), userId, EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", Map.of(), 5, null,
                Instant.now(), "key-" + UUID.randomUUID());
        n.markRendered(subject, body);
        return n;
    }

    private NotificationTemplate template(String code, int version, int trafficPct) {
        return new NotificationTemplate(UUID.randomUUID(), code, NotificationChannel.EMAIL, version,
                "Subject v" + version, "Body v" + version, "en-US", true, Instant.now(), trafficPct);
    }
}