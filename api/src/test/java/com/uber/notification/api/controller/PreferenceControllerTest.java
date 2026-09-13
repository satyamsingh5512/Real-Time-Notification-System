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
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.RoleName;
import com.uber.notification.domain.model.UserPreference;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies per-event-type channel opt-in/out and quiet-hours preference APIs. */
@WebMvcTest(controllers = PreferenceController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.infrastructure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.api\\.websocket\\..*")})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PreferenceControllerTest {

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

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void authenticate() throws Exception {
        when(jwtService.parseToken("test-jwt"))
                .thenReturn(new JwtService.AuthenticatedPrincipal(userId, "a@example.com", Set.of(RoleName.USER)));
    }

    private UserPreference samplePreference() {
        Map<NotificationChannel, Boolean> optIn = new EnumMap<>(NotificationChannel.class);
        optIn.put(NotificationChannel.EMAIL, true);
        return new UserPreference(UUID.randomUUID(), userId, EventType.ORDER_PLACED,
                optIn, false, 0, 0, Instant.now());
    }

    @Test
    void setChannelOptInReturnsUpdatedPreference() throws Exception {
        when(preferenceUseCase.setChannelOptIn(eq(userId), eq(EventType.ORDER_PLACED),
                eq(NotificationChannel.EMAIL), eq(false)))
                .thenReturn(samplePreference());

        mockMvc.perform(put("/api/v1/preferences/{eventType}/channel", "ORDER_PLACED")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","enabled":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventType").value("ORDER_PLACED"));
    }

    @Test
    void setQuietHoursReturnsUpdatedPreference() throws Exception {
        UserPreference pref = new UserPreference(UUID.randomUUID(), userId, EventType.ORDER_PLACED,
                new EnumMap<>(NotificationChannel.class), true, 22, 6, Instant.now());
        when(preferenceUseCase.setQuietHours(eq(userId), eq(EventType.MENTIONED),
                anyBoolean(), anyInt(), anyInt())).thenReturn(pref);

        mockMvc.perform(put("/api/v1/preferences/{eventType}/quiet-hours", "MENTIONED")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"startHour":22,"endHour":6}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quietHoursEnabled").value(true));
    }

    @Test
    void invalidQuietHoursRejected() throws Exception {
        mockMvc.perform(put("/api/v1/preferences/{eventType}/quiet-hours", "MENTIONED")
                        .header("Authorization", BEARER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"enabled":true,"startHour":99,"endHour":6}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedPreferenceAccessIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/preferences/{eventType}/channel", "ORDER_PLACED")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"channel":"EMAIL","enabled":false}"""))
                .andExpect(status().isForbidden());
    }
}
