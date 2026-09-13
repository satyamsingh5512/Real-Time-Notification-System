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
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.domain.model.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies RBAC on the admin template API: ROLE_ADMIN can create templates,
 * ROLE_USER receives 403 Forbidden.
 */
@WebMvcTest(controllers = TemplateController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.infrastructure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.api\\.websocket\\..*")})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class TemplateControllerTest {

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

    private NotificationTemplate sampleTemplate() {
        return new NotificationTemplate(UUID.randomUUID(), "ORDER_PLACED", NotificationChannel.EMAIL,
                1, "Hi {{name}}", "Your order {{orderId}} shipped", "en-US", true, Instant.now());
    }

    @Test
    void adminCanCreateTemplate() throws Exception {
        UUID adminId = UUID.randomUUID();
        when(jwtService.parseToken("admin-jwt")).thenReturn(
                new JwtService.AuthenticatedPrincipal(adminId, "admin@example.com", Set.of(RoleName.ADMIN)));
        when(templateUseCase.createNewVersion(eq("ORDER_PLACED"), eq(NotificationChannel.EMAIL),
                eq("en-US"), any(), any())).thenReturn(sampleTemplate());

        mockMvc.perform(post("/api/v1/admin/templates")
                        .header("Authorization", "Bearer admin-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ORDER_PLACED","channel":"EMAIL","locale":"en-US","subjectTemplate":"Hi","bodyTemplate":"Body"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_PLACED"));
    }

    @Test
    void userRoleCannotCreateTemplate() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtService.parseToken("user-jwt")).thenReturn(
                new JwtService.AuthenticatedPrincipal(userId, "user@example.com", Set.of(RoleName.USER)));

        mockMvc.perform(post("/api/v1/admin/templates")
                        .header("Authorization", "Bearer user-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"ORDER_PLACED","channel":"EMAIL","locale":"en-US","subjectTemplate":"Hi","bodyTemplate":"Body"}"""))
                .andExpect(status().isForbidden());
    }
}
