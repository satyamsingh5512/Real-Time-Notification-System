package com.uber.notification.api.controller;

import com.uber.notification.api.security.JwtAuthenticationFilter;
import com.uber.notification.api.security.JwtService;
import com.uber.notification.api.security.SecurityConfig;
import com.uber.notification.application.usecase.AdminDashboardUseCase;
import com.uber.notification.application.usecase.AuthenticateUserUseCase;
import com.uber.notification.application.usecase.BroadcastNotificationUseCase;
import com.uber.notification.application.usecase.DigestFlushUseCase;
import com.uber.notification.application.usecase.ManageNotificationTemplateUseCase;
import com.uber.notification.application.usecase.ManageUserPreferenceUseCase;
import com.uber.notification.application.usecase.MassEmailUseCase;
import com.uber.notification.application.usecase.NotificationHistoryUseCase;
import com.uber.notification.application.usecase.ScheduleNotificationUseCase;
import com.uber.notification.infrastructure.provider.email.EmailServiceProperties;
import com.uber.notification.infrastructure.provider.email.EmailServiceQuotaClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mass email is the highest-abuse-risk action in the product, so its guards are the
 * thing under test: ROLE_ADMIN only, off unless explicitly enabled, size-capped, and
 * refused outright when the upstream daily quota is already spent.
 */
@WebMvcTest(controllers = MassEmailController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.infrastructure\\..*"),
                @ComponentScan.Filter(type = FilterType.REGEX,
                        pattern = "com\\.uber\\.notification\\.api\\.websocket\\..*")})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, MassEmailControllerTest.PropertiesConfig.class})
class MassEmailControllerTest {

    private static final String BODY = """
            {"subject":"Hello","body":"Body text","variables":{},"priority":"MEDIUM"}
            """;

    /**
     * The controller resolves {@link EmailServiceProperties} through an
     * {@code ObjectProvider}, and the real one lives behind a conditional configuration
     * in the infrastructure module, which this slice excludes. Supplying it here lets the
     * guard be exercised directly.
     */
    @TestConfiguration
    static class PropertiesConfig {
        @Bean
        EmailServiceProperties emailServiceProperties() {
            EmailServiceProperties properties = new EmailServiceProperties();
            properties.setEnabled(true);
            properties.setMassMailEnabled(true);
            properties.setMassMailMaxRecipients(1_000);
            return properties;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailServiceProperties properties;

    /*
     * `UseCaseConfig` is a @Configuration, so the slice honours its @Bean methods and
     * drags in the full persistence/Redis/Kafka/Firebase graph even though
     * `excludeFilters` names only infrastructure packages. Without these leaf mocks the
     * context fails to start with "No bean named 'entityManagerFactory'".
     */
    @MockBean private AuthenticateUserUseCase authenticateUserUseCase;
    @MockBean private AdminDashboardUseCase adminDashboardUseCase;
    @MockBean private BroadcastNotificationUseCase broadcastUseCase;
    @MockBean private DigestFlushUseCase digestFlushUseCase;
    @MockBean private ManageNotificationTemplateUseCase templateUseCase;
    @MockBean private ManageUserPreferenceUseCase preferenceUseCase;
    @MockBean private NotificationHistoryUseCase historyUseCase;
    @MockBean private ScheduleNotificationUseCase scheduleUseCase;
    @MockBean private MassEmailUseCase massEmailUseCase;

    @MockBean(answer = org.mockito.Answers.RETURNS_MOCKS)
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @MockBean
    private com.uber.notification.infrastructure.persistence.jpa.NotificationJpaRepository notificationJpaRepository;
    @MockBean
    private com.uber.notification.infrastructure.persistence.jpa.UserJpaRepository userJpaRepository;
    @MockBean
    private com.uber.notification.infrastructure.persistence.jpa.UserPreferenceJpaRepository userPreferenceJpaRepository;
    @MockBean
    private com.uber.notification.infrastructure.persistence.jpa.NotificationTemplateJpaRepository notificationTemplateJpaRepository;

    @MockBean
    private com.google.firebase.messaging.FirebaseMessaging firebaseMessaging;
    @MockBean
    private software.amazon.awssdk.services.ses.SesClient sesClient;

    @MockBean
    private org.springframework.integration.support.locks.LockRegistry lockRegistry;
    @MockBean
    private org.springframework.data.redis.connection.RedisConnectionFactory redisConnectionFactory;
    @MockBean
    private org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean
    private org.springframework.kafka.core.KafkaTemplate kafkaTemplate;
    @MockBean(name = "entityManagerFactory")
    private jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EmailServiceQuotaClient quotaClient;

    @BeforeEach
    void resetGuard() {
        // The properties bean is a singleton in the slice; reset so one test's mutation
        // cannot leak into the next.
        properties.setMassMailEnabled(true);
        properties.setMassMailMaxRecipients(1_000);
    }

    @Test
    @DisplayName("ROLE_USER cannot send campaign email")
    void userIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .with(user("bob").roles("USER"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous cannot send campaign email")
    void anonymousIsRejected() throws Exception {
        // 403, not 401: SecurityConfig registers no AuthenticationEntryPoint, so Spring
        // Security's Http403ForbiddenEntryPoint answers. Documented rather than "fixed"
        // here because changing it would alter every other endpoint's behaviour.
        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("mass email is refused unless explicitly enabled")
    void refusesWhenDisabled() throws Exception {
        properties.setMassMailEnabled(false);

        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isBadRequest());

        verify(massEmailUseCase, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("preview reports the audience and the opt-out count without sending")
    void previewReportsAudience() throws Exception {
        when(massEmailUseCase.preview(any(), any(), any(), any(), any())).thenReturn(
                new MassEmailUseCase.CampaignResult("preview-id", 120, 90, 30, 0, 0, 0, false));
        when(quotaClient.fetchDailyBudget())
                .thenReturn(new EmailServiceQuotaClient.Budget(10, 100, true));

        mockMvc.perform(post("/api/v1/admin/email/campaigns/preview")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.scanned").value(120))
                .andExpect(jsonPath("$.targeted").value(90))
                .andExpect(jsonPath("$.skippedOptedOut").value(30))
                .andExpect(jsonPath("$.sent").value(0))
                .andExpect(jsonPath("$.quotaLimitPerDay").value(100));

        verify(massEmailUseCase, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a send that fits the budget dispatches")
    void sendsWhenWithinBudget() throws Exception {
        when(massEmailUseCase.preview(any(), any(), any(), any(), any())).thenReturn(
                new MassEmailUseCase.CampaignResult("preview-id", 50, 40, 10, 0, 0, 0, false));
        when(massEmailUseCase.send(any(), any(), any(), any(), any())).thenReturn(
                new MassEmailUseCase.CampaignResult("send-id", 50, 40, 10, 0, 38, 2, false));
        when(quotaClient.fetchDailyBudget())
                .thenReturn(new EmailServiceQuotaClient.Budget(5, 100, true));

        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(38))
                .andExpect(jsonPath("$.failed").value(2))
                .andExpect(jsonPath("$.blocked").doesNotExist());

        verify(massEmailUseCase).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an exhausted upstream quota blocks the send instead of failing halfway")
    void blocksWhenQuotaExhausted() throws Exception {
        when(massEmailUseCase.preview(any(), any(), any(), any(), any())).thenReturn(
                new MassEmailUseCase.CampaignResult("preview-id", 50, 40, 10, 0, 0, 0, false));
        when(quotaClient.fetchDailyBudget())
                .thenReturn(new EmailServiceQuotaClient.Budget(100, 100, true));

        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedReason").value(
                        org.hamcrest.Matchers.containsString("exhausted its daily quota")));

        verify(massEmailUseCase, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("an audience over the configured cap is refused")
    void blocksOversizedAudience() throws Exception {
        properties.setMassMailMaxRecipients(10);
        when(massEmailUseCase.preview(any(), any(), any(), any(), any())).thenReturn(
                new MassEmailUseCase.CampaignResult("preview-id", 900, 500, 400, 0, 0, 0, false));
        when(quotaClient.fetchDailyBudget())
                .thenReturn(new EmailServiceQuotaClient.Budget(0, 100, true));

        mockMvc.perform(post("/api/v1/admin/email/campaigns")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.blockedReason").value(
                        org.hamcrest.Matchers.containsString("exceeds the configured limit")));

        verify(massEmailUseCase, never()).send(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("a blank subject is a validation error")
    void rejectsBlankSubject() throws Exception {
        mockMvc.perform(post("/api/v1/admin/email/campaigns/preview")
                        .with(user("admin").roles("ADMIN"))
                        .contentType("application/json")
                        .content("{\"subject\":\"\",\"body\":\"Body\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quota lookup degrades to unknown rather than failing the request")
    void quotaIsBestEffort() throws Exception {
        when(quotaClient.fetchDailyBudget())
                .thenReturn(EmailServiceQuotaClient.Budget.unknown());

        mockMvc.perform(get("/api/v1/admin/email/quota").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reachable").value(false));
    }
}