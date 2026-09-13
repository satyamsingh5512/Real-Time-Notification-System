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
import com.uber.notification.common.exception.AuthenticationFailedException;
import com.uber.notification.common.exception.ValidationException;
import com.uber.notification.domain.model.RoleName;
import com.uber.notification.domain.model.User;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies POST /api/v1/auth/register + /login: success shape, duplicate-email 400,
 * short-password 400 (bean validation), and wrong-credential 401.
 */
@WebMvcTest(controllers = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.infrastructure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.api\\.websocket\\..*")})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})class AuthControllerTest {

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

    private User sampleUser() {
        return new User(UUID.randomUUID(), "alice@example.com", "hashed",
                "Alice", Set.of(RoleName.USER), true, Instant.now(), Instant.now());
    }

    @Test
    void registerReturns201WithJwt() throws Exception {
        User user = sampleUser();
        when(authenticateUserUseCase.register(anyString(), anyString(), anyString())).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123","displayName":"Alice"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("signed-jwt"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void registerDuplicateEmailReturns400() throws Exception {
        when(authenticateUserUseCase.register(anyString(), anyString(), anyString()))
                .thenThrow(new ValidationException("Email already registered: alice@example.com"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123","displayName":"Alice"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerShortPasswordRejectedByBeanValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"short","displayName":"Alice"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginReturns200WithJwtOnValidCredentials() throws Exception {
        User user = sampleUser();
        when(authenticateUserUseCase.authenticate("alice@example.com", "password123")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("signed-jwt");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed-jwt"));
    }

    @Test
    void loginWrongCredentialsReturns401() throws Exception {
        when(authenticateUserUseCase.authenticate(anyString(), anyString()))
                .thenThrow(new AuthenticationFailedException("Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"wrongpassword"}"""))
                .andExpect(status().isUnauthorized());
    }
}
