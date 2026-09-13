package com.uber.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import com.uber.notification.infrastructure.kafka.consumer.RetryTopicConsumer;
import com.uber.notification.infrastructure.kafka.dto.NotificationRefMessage;
import com.uber.notification.infrastructure.kafka.producer.KafkaRetryPublisherAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the retry → DLQ flow without a live broker:
 * <ol>
 *   <li>Transient provider failure (retryable) → message lands on the 30s retry bucket.</li>
 *   <li>Attempts exhausted / permanent failure → message lands on {@code notification.dlq}.</li>
 *   <li>Retry consumer defers far-future messages (no early redelivery, no thread starvation)
 *       and redelivers due messages.</li>
 *   <li>Bucket routing: short vs long backoffs land on different bucket topics.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RetryTopicIntegrationTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private RecipientResolverPort recipientResolver;
    @Mock
    private NotificationProvider provider;

    private ObjectMapper objectMapper;
    private KafkaRetryPublisherAdapter retryPublisher;
    private DeliverNotificationUseCase deliverUseCase;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        retryPublisher = new KafkaRetryPublisherAdapter(kafkaTemplate, objectMapper, null);
        NotificationProviderRegistry registry = new NotificationProviderRegistry(
                Map.of(NotificationChannel.EMAIL, provider));
        deliverUseCase = new DeliverNotificationUseCase(notificationRepository, templateRepository,
                registry, recipientResolver, retryPublisher);
        org.mockito.Mockito.lenient().when(notificationRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.lenient()
                .when(templateRepository.findActiveByCodeAndChannel(any(), any()))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(recipientResolver.resolve(any()))
                .thenReturn(new ProviderRecipient("a@b.com", null, null, "u1"));
    }

    private Notification newNotification(int maxAttempts) {
        return new Notification(UUID.randomUUID(), UUID.randomUUID(), EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", Map.of("message", "hi"), maxAttempts,
                null, Instant.now(), "idem-" + UUID.randomUUID());
    }

    @Test
    void transientFailurePublishesToRetryBucketWithBackoff() {
        Notification n = newNotification(5);
        doThrow(new NotificationDeliveryException("SES 500", true)).when(provider).send(any(), any());

        deliverUseCase.execute(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.RETRYING);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), any(String.class), any(String.class));
        // First-attempt backoff (~5s) → 30s bucket.
        assertThat(topic.getValue()).isEqualTo(KafkaTopics.NOTIFICATION_RETRY_30S);
    }

    @Test
    void exhaustedRetriesPublishToDlq() {
        Notification n = newNotification(1); // single attempt → immediately exhausted
        doThrow(new NotificationDeliveryException("SES 500", true)).when(provider).send(any(), any());

        deliverUseCase.execute(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), any(String.class), any(String.class));
        assertThat(topic.getValue()).isEqualTo(KafkaTopics.NOTIFICATION_DLQ);
    }

    @Test
    void permanentFailureGoesStraightToDlq() {
        Notification n = newNotification(5);
        doThrow(new NotificationDeliveryException("invalid address", false)).when(provider).send(any(), any());

        deliverUseCase.execute(n);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.DEAD_LETTERED);
        verify(kafkaTemplate).send(org.mockito.ArgumentMatchers.eq(KafkaTopics.NOTIFICATION_DLQ),
                any(String.class), any(String.class));
    }

    @Test
    void bucketRoutingSendsLongBackoffTo30mBucket() throws Exception {
        Notification n = newNotification(5);
        retryPublisher.publishForRetry(n, Duration.ofMinutes(20));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), any(String.class), any(String.class));
        assertThat(topic.getValue()).isEqualTo(KafkaTopics.NOTIFICATION_RETRY_30M);
    }

    @Test
    void retryConsumerDefersFarFutureMessageWithoutRedelivering() throws Exception {
        UUID id = UUID.randomUUID();
        NotificationRefMessage msg = new NotificationRefMessage(id, 1, Instant.now().plus(Duration.ofHours(1)), null);
        RetryTopicConsumer consumer = new RetryTopicConsumer(notificationRepository, deliverUseCase, objectMapper);

        consumer.onRetry30s(objectMapper.writeValueAsString(msg));

        verify(notificationRepository, never()).findById(any());
    }

    @Test
    void retryConsumerRedeliversDueMessage() throws Exception {
        UUID id = UUID.randomUUID();
        Notification n = newNotification(5);
        NotificationRefMessage msg = new NotificationRefMessage(id, 1, Instant.now().minusSeconds(5), null);
        when(notificationRepository.findById(id)).thenReturn(Optional.of(n));
        doNothing().when(provider).send(any(), any()); // succeed on retry
        RetryTopicConsumer consumer = new RetryTopicConsumer(notificationRepository, deliverUseCase, objectMapper);

        consumer.onRetry(objectMapper.writeValueAsString(msg));

        verify(notificationRepository).findById(id);
        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
    }
}
