package com.uber.notification.infrastructure.persistence;

import com.uber.notification.domain.model.*;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end persistence test: boots the real Spring context against a throwaway Postgres
 * container (Testcontainers) and exercises the JPA adapters through the domain repository
 * ports, verifying entity<->domain mapping, JSONB columns, and query methods actually work
 * against a real database engine (not just an in-memory mock).
 */
@Testcontainers
@SpringBootTest(classes = com.uber.notification.infrastructure.PersistenceTestApplication.class)
class NotificationRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("notification_platform_test");

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

    @Test
    void savesAndRetrievesUserWithRolesAndContactInfo() {
        User user = new User(UUID.randomUUID(), "test@example.com", "hash", "Test User",
                "+15551234567", "fcm-token-abc", Set.of(RoleName.USER), true, Instant.now(), Instant.now());

        User saved = userRepository.save(user);
        var found = userRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        assertThat(found.get().getPhoneNumber()).isEqualTo("+15551234567");
        assertThat(found.get().hasRole(RoleName.USER)).isTrue();
    }

    @Test
    void savesNotificationWithJsonbPayloadAndFindsByIdempotencyKey() {
        User user = userRepository.save(new User(UUID.randomUUID(), "notif-user@example.com", "hash",
                "Notif User", Set.of(RoleName.USER), true, Instant.now(), Instant.now()));

        Notification notification = new Notification(UUID.randomUUID(), user.getId(), EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED", java.util.Map.of("orderId", "XYZ-1"), 5,
                null, Instant.now(), "idem-test-1");

        notificationRepository.save(notification);

        var found = notificationRepository.findByIdempotencyKey("idem-test-1");
        assertThat(found).isPresent();
        assertThat(found.get().getPayload()).containsEntry("orderId", "XYZ-1");
        assertThat(found.get().getStatus()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void unreadCountReflectsOnlyUnreadNonDeletedNotifications() {
        User user = userRepository.save(new User(UUID.randomUUID(), "unread-user@example.com", "hash",
                "Unread User", Set.of(RoleName.USER), true, Instant.now(), Instant.now()));

        Notification n1 = notificationRepository.save(new Notification(UUID.randomUUID(), user.getId(),
                EventType.LIKE_RECEIVED, NotificationChannel.IN_APP, "LIKE_RECEIVED", null, 5, null,
                Instant.now(), "idem-unread-1"));
        Notification n2 = notificationRepository.save(new Notification(UUID.randomUUID(), user.getId(),
                EventType.LIKE_RECEIVED, NotificationChannel.IN_APP, "LIKE_RECEIVED", null, 5, null,
                Instant.now(), "idem-unread-2"));

        n2.markRead();
        notificationRepository.save(n2);

        assertThat(notificationRepository.countUnread(user.getId())).isEqualTo(1);
    }

    @Test
    void savesAndFindsUserPreferenceWithJsonbChannelMap() {
        User user = userRepository.save(new User(UUID.randomUUID(), "pref-user@example.com", "hash",
                "Pref User", Set.of(RoleName.USER), true, Instant.now(), Instant.now()));
        UUID userId = user.getId();
        var optIn = new EnumMap<NotificationChannel, Boolean>(NotificationChannel.class);
        optIn.put(NotificationChannel.EMAIL, false);
        optIn.put(NotificationChannel.PUSH, true);

        UserPreference preference = new UserPreference(UUID.randomUUID(), userId, EventType.MENTIONED,
                optIn, true, 22, 6, Instant.now());

        userPreferenceRepository.save(preference);

        var found = userPreferenceRepository.findByUserIdAndEventType(userId, EventType.MENTIONED);
        assertThat(found).isPresent();
        assertThat(found.get().isChannelEnabled(NotificationChannel.EMAIL)).isFalse();
        assertThat(found.get().isChannelEnabled(NotificationChannel.PUSH)).isTrue();
        assertThat(found.get().isWithinQuietHours(23)).isTrue();
    }
}
