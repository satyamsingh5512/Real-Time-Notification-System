package com.uber.notification.api.controller;

import com.uber.notification.api.security.JwtAuthenticationFilter;
import com.uber.notification.api.security.JwtService;
import com.google.firebase.messaging.FirebaseMessaging;
import com.uber.notification.infrastructure.persistence.jpa.NotificationJpaRepository;
import com.uber.notification.infrastructure.persistence.jpa.NotificationTemplateJpaRepository;
import com.uber.notification.infrastructure.persistence.jpa.UserJpaRepository;
import com.uber.notification.infrastructure.persistence.jpa.UserPreferenceJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.integration.support.locks.LockRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import software.amazon.awssdk.services.ses.SesClient;
import com.uber.notification.api.security.SecurityConfig;
import com.uber.notification.application.usecase.AuthenticateUserUseCase;
import com.uber.notification.application.usecase.AdminDashboardUseCase;
import com.uber.notification.application.usecase.BroadcastNotificationUseCase;
import com.uber.notification.application.usecase.ManageNotificationTemplateUseCase;
import com.uber.notification.application.usecase.ManageUserPreferenceUseCase;
import com.uber.notification.application.usecase.NotificationHistoryUseCase;
import com.uber.notification.application.usecase.ScheduleNotificationUseCase;
import com.uber.notification.common.exception.ResourceNotFoundException;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.model.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the notification inbox API: paginated history, unread count,
 * mark read/unread, and IDOR prevention (User A cannot touch User B's rows —
 * the use case throws ResourceNotFound, surfaced as 404).
 */
@WebMvcTest(controllers = NotificationController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.infrastructure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.api\\.websocket\\..*")})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class NotificationControllerTest {

    private static final String BEARER = "Bearer test-jwt";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticateUserUseCase authenticateUserUseCase;

    @MockBean
    private AdminDashboardUseCase adminDashboardUseCase;

    @MockBean
    private BroadcastNotificationUseCase broadcastUseCase;

    @MockBean
    private NotificationHistoryUseCase historyUseCase;

    @MockBean
    private ManageUserPreferenceUseCase preferenceUseCase;

    @MockBean
    private ManageNotificationTemplateUseCase templateUseCase;

    @MockBean
    private ScheduleNotificationUseCase scheduleNotificationUseCase;

    // Deep-stub mock: spring-kafka's container observation calls registry.config()
    // at startup, which returns null on a default mock (NPE). RETURNS_MOCKS avoids that.
    @MockBean(answer = org.mockito.Answers.RETURNS_MOCKS)
    private MeterRegistry meterRegistry;

    @MockBean
    private NotificationJpaRepository notificationJpaRepository;

    @MockBean
    private UserJpaRepository userJpaRepository;

    @MockBean
    private UserPreferenceJpaRepository userPreferenceJpaRepository;

    @MockBean
    private NotificationTemplateJpaRepository notificationTemplateJpaRepository;

    @MockBean
    private FirebaseMessaging firebaseMessaging;

    @MockBean
    private SesClient sesClient;

    @MockBean
    private LockRegistry lockRegistry;

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @MockBean
    private KafkaTemplate kafkaTemplate;

    @MockBean(name = "entityManagerFactory")
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockBean
    private JwtService jwtService;

    private final UUID userA = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    @BeforeEach
    void authenticateAsUserA() throws Exception {
        when(jwtService.parseToken("test-jwt"))
                .thenReturn(new JwtService.AuthenticatedPrincipal(userA, "a@example.com", Set.of(RoleName.USER)));
    }

    private Notification sampleNotification() {
        Notification n = new Notification(notificationId, userA, EventType.ORDER_PLACED,
                NotificationChannel.IN_APP, "ORDER_PLACED", Map.of("message", "hi"),
                5, null, Instant.now(), "idem-1");
        n.markRendered("Subject", "Body");
        return n;
    }

    @Test
    void getHistoryReturnsPaginatedInbox() throws Exception {
        when(historyUseCase.getHistory(eq(userA), anyBoolean(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sampleNotification()));

        mockMvc.perform(get("/api/v1/notifications?page=0&size=20").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()));
    }

    @Test
    void getUnreadCountReturnsCount() throws Exception {
        when(historyUseCase.getUnreadCount(userA)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));
    }

    @Test
    void markReadReturnsUpdatedNotification() throws Exception {
        Notification n = sampleNotification();
        n.markRead();
        when(historyUseCase.markRead(notificationId, userA)).thenReturn(n);

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markUnreadReturnsUpdatedNotification() throws Exception {
        when(historyUseCase.markUnread(eq(notificationId), eq(userA))).thenReturn(sampleNotification());

        mockMvc.perform(patch("/api/v1/notifications/{id}/unread", notificationId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    void idorPreventedWhenMarkingAnotherUsersNotification() throws Exception {
        // User A's token, but the row belongs to User B → use case throws → 404, no leak.
        when(historyUseCase.markRead(any(UUID.class), eq(userA)))
                .thenThrow(new ResourceNotFoundException("Notification not found: " + notificationId));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", BEARER))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedInboxAccessIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteReturns200OnOwnedNotification() throws Exception {
        mockMvc.perform(delete("/api/v1/notifications/{id}", notificationId)
                        .header("Authorization", BEARER))
                .andExpect(status().isOk());
    }

    @Test
    void notificationStatusFieldIsExposed() throws Exception {
        Notification n = sampleNotification();
        assert n.getStatus() == NotificationStatus.PENDING;
        when(historyUseCase.getHistory(eq(userA), anyBoolean(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(n));

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].priority").value("MEDIUM"));
    }

    @Test
    void historySupportsTypeFilter() throws Exception {
        when(historyUseCase.getHistory(eq(userA), anyBoolean(), eq(EventType.MENTIONED),
                any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sampleNotification()));

        mockMvc.perform(get("/api/v1/notifications?type=MENTIONED").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(notificationId.toString()));
    }

    @Test
    void markAllReadReturnsCount() throws Exception {
        when(historyUseCase.markAllRead(userA)).thenReturn(4);

        mockMvc.perform(patch("/api/v1/notifications/read-all").header("Authorization", BEARER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedRead").value(4));
    }
}
