package com.uber.notification.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uber.notification.application.usecase.DeliverNotificationUseCase;
import com.uber.notification.application.usecase.ProcessIncomingEventUseCase;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.RoleName;
import com.uber.notification.domain.model.User;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;
import com.uber.notification.infrastructure.PersistenceTestApplication;
import com.uber.notification.infrastructure.kafka.consumer.NotificationEventConsumer;
import com.uber.notification.infrastructure.kafka.dto.EventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Boots a real Postgres (Testcontainers) with the real persistence adapters and
 * fan-out use case, then drives {@link NotificationEventConsumer} directly with the
 * exact JSON wire format produced for {@code events.order.placed}.
 *
 * <p>Asserts the consumer ingests the event and persists one Notification row per
 * routed channel (EMAIL, PUSH, IN_APP for ORDER_PLACED). Delivery itself is mocked —
 * provider I/O is covered by {@code DeliverNotificationUseCaseTest}.
 */
@Testcontainers
@SpringBootTest(classes = PersistenceTestApplication.class)
class NotificationEventConsumerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_platform_events_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserPreferenceRepository userPreferenceRepository;

    @MockBean
    private DeliverNotificationUseCase deliverNotificationUseCase;

    private ObjectMapper objectMapper;
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ProcessIncomingEventUseCase processUseCase =
                new ProcessIncomingEventUseCase(notificationRepository, userPreferenceRepository);
        // Poison-pill publisher + metrics are optional (null-safe) — not under test here.
        consumer = new NotificationEventConsumer(processUseCase, deliverNotificationUseCase, objectMapper, null, null);
    }

    @Test
    void orderPlacedEventIsIngestedAndPersistedPerChannel() throws Exception {
        User user = userRepository.save(new User(UUID.randomUUID(), "buyer@example.com", "hash",
                "Buyer", Set.of(RoleName.USER), true, Instant.now(), Instant.now()));

        EventMessage event = new EventMessage(UUID.randomUUID().toString(), EventType.ORDER_PLACED,
                user.getId().toString(), Map.of("orderId", "ORD-1", "amount", "499"), Instant.now());

        consumer.onOrderPlaced(objectMapper.writeValueAsString(event));

        List<com.uber.notification.domain.model.Notification> history =
                notificationRepository.findHistoryForUser(user.getId(), false, 0, 20);
        // ORDER_PLACED routes to EMAIL + PUSH + IN_APP.
        assertThat(history).hasSize(3);
        assertThat(history).allMatch(n -> n.getUserId().equals(user.getId()));
        verify(deliverNotificationUseCase, atLeastOnce()).execute(any());
    }

    @Test
    void duplicateDeliveryDoesNotDuplicateNotifications() throws Exception {
        User user = userRepository.save(new User(UUID.randomUUID(), "dup@example.com", "hash",
                "Dup", Set.of(RoleName.USER), true, Instant.now(), Instant.now()));

        String eventId = UUID.randomUUID().toString();
        EventMessage event = new EventMessage(eventId, EventType.ORDER_PLACED,
                user.getId().toString(), Map.of("orderId", "ORD-2"), Instant.now());
        String payload = objectMapper.writeValueAsString(event);

        consumer.onOrderPlaced(payload);
        consumer.onOrderPlaced(payload); // redelivered (same eventId → same idempotency keys)

        assertThat(notificationRepository.findHistoryForUser(user.getId(), false, 0, 20)).hasSize(3);
    }

    @Test
    void malformedPayloadIsDroppedWithoutThrowing() {
        // Must never propagate: Spring Kafka would otherwise retry a poison pill forever.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> consumer.onOrderPlaced("{not valid json"));
    }
}
