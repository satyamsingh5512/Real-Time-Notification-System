package com.uber.notification.application.usecase;

import com.uber.notification.application.event.DomainEvent;
import com.uber.notification.domain.model.*;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProcessIncomingEventUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserPreferenceRepository preferenceRepository;

    private ProcessIncomingEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessIncomingEventUseCase(notificationRepository, preferenceRepository);
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void fansOutToAllDefaultChannelsWhenNoPreferenceExists() {
        UUID userId = UUID.randomUUID();
        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.ORDER_PLACED))
                .thenReturn(Optional.empty());

        DomainEvent event = new DomainEvent("evt-1", EventType.ORDER_PLACED, userId,
                Map.of("orderId", "A1"), Instant.now());

        List<Notification> result = useCase.execute(event);

        // ORDER_PLACED routes to EMAIL, PUSH, IN_APP by default.
        assertThat(result).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(NotificationChannel.EMAIL, NotificationChannel.PUSH, NotificationChannel.IN_APP);
    }

    @Test
    void respectsChannelOptOut() {
        UUID userId = UUID.randomUUID();
        Map<NotificationChannel, Boolean> optIn = new EnumMap<>(NotificationChannel.class);
        optIn.put(NotificationChannel.EMAIL, false);
        UserPreference preference = new UserPreference(UUID.randomUUID(), userId, EventType.ORDER_PLACED,
                optIn, false, 0, 0, Instant.now());

        when(preferenceRepository.findByUserIdAndEventType(userId, EventType.ORDER_PLACED))
                .thenReturn(Optional.of(preference));

        DomainEvent event = new DomainEvent("evt-2", EventType.ORDER_PLACED, userId, Map.of(), Instant.now());

        List<Notification> result = useCase.execute(event);

        assertThat(result).extracting(Notification::getChannel).doesNotContain(NotificationChannel.EMAIL);
    }

    @Test
    void skipsDuplicateEventsByIdempotencyKey() {
        UUID userId = UUID.randomUUID();
        reset(notificationRepository);
        when(preferenceRepository.findByUserIdAndEventType(any(), any())).thenReturn(Optional.empty());
        when(notificationRepository.findByIdempotencyKey(any())).thenReturn(Optional.of(mock(Notification.class)));

        DomainEvent event = new DomainEvent("evt-3", EventType.PAYMENT_SUCCESS, userId, Map.of(), Instant.now());

        List<Notification> result = useCase.execute(event);

        assertThat(result).isEmpty();
        verify(notificationRepository, never()).save(any());
    }
}
