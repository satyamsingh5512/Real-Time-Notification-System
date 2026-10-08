package com.uber.notification.application.usecase;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationIntent;
import com.uber.notification.domain.model.NotificationPriority;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The behaviour worth protecting in {@link MassEmailUseCase} is audience filtering: email
 * leaves the building, so a user who opted out must never be targeted. The in-app
 * broadcast has no equivalent filter, which is why this needs its own tests.
 */
@ExtendWith(MockitoExtension.class)
class MassEmailUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPreferenceRepository preferenceRepository;
    @Mock
    private DeliverNotificationUseCase deliverNotificationUseCase;

    private MassEmailUseCase useCase;

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new MassEmailUseCase(notificationRepository, userRepository,
                preferenceRepository, deliverNotificationUseCase);
    }

    /** A preference row with a single channel switched. */
    /**
     * Simulates a successful delivery. The real DeliverNotificationUseCase marks the row
     * SENT; a void mock cannot, so without this every send would be counted as failed by
     * the row-status check.
     */
    private void markSentOnExecute() {
        doAnswer(invocation -> {
            ((com.uber.notification.domain.model.Notification) invocation.getArgument(0)).markSent();
            return null;
        }).when(deliverNotificationUseCase).execute(any());
    }

    private static UserPreference pref(UUID userId, EventType eventType, NotificationIntent intent,
                                       NotificationChannel channel, boolean enabled) {
        Map<NotificationChannel, Boolean> optIn = new EnumMap<>(NotificationChannel.class);
        optIn.put(channel, enabled);
        UserPreference p = new UserPreference(UUID.randomUUID(), userId, eventType, optIn,
                false, 0, 0, java.time.Instant.now(), intent, true,
                com.uber.notification.domain.model.DigestCadence.OFF, 2, 8);
        return p;
    }

    @Test
    @DisplayName("a user with no preference rows is targeted — absence is not opt-out")
    void targetsUsersWithNoPreferences() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of());

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.targeted()).isEqualTo(1);
        assertThat(result.skippedOptedOut()).isZero();
        assertThat(result.scanned()).isEqualTo(1);
    }

    @Test
    @DisplayName("a user who switched EMAIL off is skipped, not targeted")
    void skipsChannelOptOut() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of(
                pref(ALICE, EventType.ORDER_PLACED, NotificationIntent.ALL,
                        NotificationChannel.EMAIL, false)));

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.targeted()).isZero();
        assertThat(result.skippedOptedOut()).isEqualTo(1);
    }

    @Test
    @DisplayName("a user who muted the event type is skipped")
    void skipsMutedIntent() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of(
                pref(ALICE, EventType.ORDER_PLACED, NotificationIntent.MUTE,
                        NotificationChannel.EMAIL, true)));

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.targeted()).isZero();
        assertThat(result.skippedOptedOut()).isEqualTo(1);
    }

    @Test
    @DisplayName("opting out of EMAIL under a different event type still suppresses the campaign")
    void respectsOptOutRegardlessOfEventType() {
        // A user who muted ORDER_PLACED has said they do not want email. Silently emailing
        // them a broadcast because the opt-out row is keyed by another event type is
        // exactly the behaviour that gets a sender blocked.
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of(
                pref(ALICE, EventType.LIKE_RECEIVED, NotificationIntent.MUTE,
                        NotificationChannel.EMAIL, false)));

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.targeted()).isZero();
    }

    @Test
    @DisplayName("opt-out of a different channel does not suppress an email campaign")
    void doesNotConfuseChannels() {
        // Turning off PUSH is not a request to stop email.
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of(
                pref(ALICE, EventType.ORDER_PLACED, NotificationIntent.ALL,
                        NotificationChannel.PUSH, false)));

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.targeted()).isEqualTo(1);
    }

    @Test
    @DisplayName("preview sends nothing")
    void previewDoesNotSend() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE, BOB));
        when(preferenceRepository.findAllByUserId(any())).thenReturn(List.of());

        useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        verify(notificationRepository, times(0)).save(any());
        verify(deliverNotificationUseCase, times(0)).execute(any());
    }

    @Test
    @DisplayName("send persists one row per targeted user and delivers each")
    void sendFansOut() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE, BOB));
        when(preferenceRepository.findAllByUserId(any())).thenReturn(List.of());
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        markSentOnExecute();

        MassEmailUseCase.CampaignResult result =
                useCase.send(null, "Hello {{name}}", "Body", Map.of("name", "there"),
                        NotificationPriority.HIGH);

        assertThat(result.targeted()).isEqualTo(2);
        assertThat(result.sent()).isEqualTo(2);
        assertThat(result.failed()).isZero();
        verify(notificationRepository, times(2)).save(any());
        verify(deliverNotificationUseCase, times(2)).execute(any());
    }

    @Test
    @DisplayName("one failing recipient does not abort the campaign")
    void isolatesPerRecipientFailure() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE, BOB));
        when(preferenceRepository.findAllByUserId(any())).thenReturn(List.of());
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        // First recipient: the provider throws, so the row never reaches SENT. Second: it
        // does. This is the real shape of the pipeline, where execute() records the
        // outcome on the row and does not rethrow.
        doAnswer(invocation -> {
            throw new com.uber.notification.common.exception.NotificationDeliveryException(
                    "down", true);
        }).doAnswer(invocation -> {
            ((com.uber.notification.domain.model.Notification) invocation.getArgument(0)).markSent();
            return null;
        }).when(deliverNotificationUseCase).execute(any());

        MassEmailUseCase.CampaignResult result =
                useCase.send(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        // The healthy recipient still received mail.
        verify(deliverNotificationUseCase, times(2)).execute(any());
    }

    @Test
    @DisplayName("templates are rendered before the row is persisted")
    void rendersTemplatesInline() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(ALICE)).thenReturn(List.of());
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());

        useCase.send(null, "Hello {{name}}", "Order {{orderId}} shipped",
                Map.of("name", "Avery", "orderId", "A4821"), NotificationPriority.MEDIUM);

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.uber.notification.domain.model.Notification.class);
        verify(notificationRepository).save(captor.capture());
        com.uber.notification.domain.model.Notification saved = captor.getValue();

        assertThat(saved.getRenderedSubject()).isEqualTo("Hello Avery");
        assertThat(saved.getRenderedBody()).isEqualTo("Order A4821 shipped");
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
        assertThat(saved.getIdempotencyKey()).startsWith("mass-email:");
    }

    @Test
    @DisplayName("pagination stops on a short page")
    void stopsOnShortPage() {
        // A short page ends the scan, so a second call is never made.
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(any())).thenReturn(List.of());

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.scanned()).isEqualTo(1);
        verify(userRepository, times(1)).findAllIds(anyInt(), anyInt());
    }

    @Test
    @DisplayName("an empty user base yields an empty, non-error result")
    void emptyAudience() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of());

        MassEmailUseCase.CampaignResult result =
                useCase.preview(null, "Subject", "Body", Map.of(), NotificationPriority.MEDIUM);

        assertThat(result.scanned()).isZero();
        assertThat(result.targeted()).isZero();
        assertThat(result.campaignId()).isNotBlank();
    }
}