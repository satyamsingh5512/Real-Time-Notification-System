package com.uber.notification.application.usecase;

import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.*;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliverNotificationUseCaseTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private NotificationProviderRegistry providerRegistry;
    @Mock
    private RecipientResolverPort recipientResolver;
    @Mock
    private RetryPublisherPort retryPublisher;
    @Mock
    private NotificationProvider provider;

    private DeliverNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeliverNotificationUseCase(notificationRepository, templateRepository,
                providerRegistry, recipientResolver, retryPublisher);
        when(notificationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(templateRepository.findActiveByCodeAndChannel(any(), any())).thenReturn(Optional.empty());
        when(recipientResolver.resolve(any())).thenReturn(new ProviderRecipient("a@b.com", null, null, "u1"));
    }

    private Notification newNotification(int maxAttempts) {
        return new Notification(UUID.randomUUID(), UUID.randomUUID(), EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", Map.of("message", "hi"), maxAttempts,
                null, Instant.now(), "idem-" + UUID.randomUUID());
    }

    @Test
    void marksSentOnSuccessfulDelivery() {
        Notification notification = newNotification(3);
        when(providerRegistry.resolve(NotificationChannel.EMAIL)).thenReturn(Optional.of(provider));

        useCase.execute(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(provider).send(eq(notification), any());
        verifyNoInteractions(retryPublisher);
    }

    @Test
    void schedulesRetryOnTransientFailureWithAttemptsRemaining() {
        Notification notification = newNotification(5);
        when(providerRegistry.resolve(NotificationChannel.EMAIL)).thenReturn(Optional.of(provider));
        doThrow(new NotificationDeliveryException("timeout", true)).when(provider).send(any(), any());

        useCase.execute(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        verify(retryPublisher).publishForRetry(eq(notification), any(Duration.class));
        verify(retryPublisher, never()).publishToDeadLetter(any(), any());
    }

    @Test
    void routesToDeadLetterWhenAttemptsExhausted() {
        Notification notification = newNotification(1); // maxAttempts = 1, so first failure exhausts it
        when(providerRegistry.resolve(NotificationChannel.EMAIL)).thenReturn(Optional.of(provider));
        doThrow(new NotificationDeliveryException("timeout", true)).when(provider).send(any(), any());

        useCase.execute(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        verify(retryPublisher).publishToDeadLetter(eq(notification), any());
        verify(retryPublisher, never()).publishForRetry(any(), any());
    }

    @Test
    void routesToDeadLetterImmediatelyOnPermanentFailure() {
        Notification notification = newNotification(5);
        when(providerRegistry.resolve(NotificationChannel.EMAIL)).thenReturn(Optional.of(provider));
        doThrow(new NotificationDeliveryException("invalid address", false)).when(provider).send(any(), any());

        useCase.execute(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        verify(retryPublisher).publishToDeadLetter(eq(notification), any());
        verify(retryPublisher, never()).publishForRetry(any(), any());
    }

    @Test
    void deadLettersWhenNoProviderRegisteredForChannel() {
        Notification notification = newNotification(5);
        when(providerRegistry.resolve(NotificationChannel.EMAIL)).thenReturn(Optional.empty());

        useCase.execute(notification);

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
    }
}
