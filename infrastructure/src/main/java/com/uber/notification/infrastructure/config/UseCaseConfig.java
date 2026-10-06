package com.uber.notification.infrastructure.config;

import com.uber.notification.application.port.DeliveryMetricsPort;
import com.uber.notification.application.port.PasswordHasher;
import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.usecase.*;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Composition root for the application layer's use cases. Use cases are plain Java classes
 * with constructor-injected ports (no Spring annotations in the `application` module itself,
 * keeping it framework-agnostic), so they are wired up here as @Bean methods instead of
 * relying on classpath component scanning.
 */
@Configuration
public class UseCaseConfig {

    /**
     * Indexes the provider beans by channel.
     *
     * <p>Several providers can legitimately claim the same channel — {@code MockEmailProvider}
     * and {@code SesEmailProvider} both exist, and enabling
     * {@code notification.email-service.enabled} adds a third
     * ({@code EmailServiceProvider}). The previous merge function only recognised names
     * beginning with {@code Mock}, so two non-mock EMAIL providers resolved to whichever
     * bean Spring happened to list last: silent, non-deterministic, and exactly the kind
     * of bug that only shows up in production.
     *
     * <p>Selection is now explicit and deterministic, by declared precedence:
     * <ol>
     *   <li>a provider bean annotated {@code @Primary} wins — this is how the email
     *       service takes over from SES without either being conditionally excluded</li>
     *   <li>otherwise a mock, so the {@code local} profile logs instead of calling
     *       cloud APIs</li>
     *   <li>otherwise the first bean by class name, for stable ordering regardless of
     *       classpath scanning order</li>
     * </ol>
     */
    @Bean
    public NotificationProviderRegistry notificationProviderRegistry(List<NotificationProvider> providers) {
        Map<NotificationChannel, NotificationProvider> byChannel = new EnumMap<>(NotificationChannel.class);
        for (NotificationProvider candidate : providers) {
            NotificationChannel channel = candidate.supportedChannel();
            byChannel.merge(channel, candidate, UseCaseConfig::preferProvider);
        }
        return new NotificationProviderRegistry(byChannel);
    }

    private static NotificationProvider preferProvider(NotificationProvider incumbent,
                                                      NotificationProvider challenger) {
        if (isPrimary(challenger) != isPrimary(incumbent)) {
            return isPrimary(challenger) ? challenger : incumbent;
        }
        boolean incumbentMock = isMock(incumbent);
        boolean challengerMock = isMock(challenger);
        if (incumbentMock != challengerMock) {
            return challengerMock ? challenger : incumbent;
        }
        // Final tie-break so the winner never depends on bean ordering.
        return challenger.getClass().getSimpleName().compareTo(incumbent.getClass().getSimpleName()) < 0
                ? challenger
                : incumbent;
    }

    private static boolean isMock(NotificationProvider provider) {
        return provider.getClass().getSimpleName().startsWith("Mock");
    }

    /**
     * Read through to the target class. A CGLIB-proxied bean does not carry its own
     * class-level annotations, so a plain {@code getClass().isAnnotationPresent} would
     * report false for a proxied {@code @Primary} provider and silently demote it —
     * re-introducing exactly the non-determinism this method exists to remove.
     */
    private static boolean isPrimary(NotificationProvider provider) {
        return AnnotationUtils.findAnnotation(AopUtils.getTargetClass(provider), Primary.class) != null;
    }

    @Bean
    public com.uber.notification.application.policy.FrequencyCapPolicy frequencyCapPolicy(
            NotificationRepository notificationRepository) {
        return new com.uber.notification.application.policy.FrequencyCapPolicy(notificationRepository);
    }

    @Bean
    public ProcessIncomingEventUseCase processIncomingEventUseCase(
            NotificationRepository notificationRepository, UserPreferenceRepository preferenceRepository,
            com.uber.notification.application.policy.FrequencyCapPolicy frequencyCapPolicy) {
        return new ProcessIncomingEventUseCase(notificationRepository, preferenceRepository, frequencyCapPolicy);
    }

    @Bean
    public com.uber.notification.application.template.TemplateRolloutSelector templateRolloutSelector(
            NotificationTemplateRepository templateRepository) {
        return new com.uber.notification.application.template.TemplateRolloutSelector(templateRepository);
    }

    @Bean
    public DigestFlushUseCase digestFlushUseCase(NotificationRepository notificationRepository) {
        return new DigestFlushUseCase(notificationRepository);
    }

    @Bean
    public DeliverNotificationUseCase deliverNotificationUseCase(
            NotificationRepository notificationRepository,
            NotificationTemplateRepository templateRepository,
            NotificationProviderRegistry providerRegistry,
            RecipientResolverPort recipientResolver,
            RetryPublisherPort retryPublisher,
            // Optional: present in the full app (Micrometer adapter), absent in persistence-only test slice.
            @org.springframework.beans.factory.annotation.Autowired(required = false) DeliveryMetricsPort deliveryMetrics,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.uber.notification.application.port.DeliveryGuardPort deliveryGuard,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.uber.notification.application.template.TemplateRolloutSelector rolloutSelector) {
        return new DeliverNotificationUseCase(notificationRepository, templateRepository,
                providerRegistry, recipientResolver, retryPublisher,
                deliveryMetrics == null ? DeliveryMetricsPort.NOOP : deliveryMetrics,
                deliveryGuard, rolloutSelector);
    }

    @Bean
    public ManageUserPreferenceUseCase manageUserPreferenceUseCase(UserPreferenceRepository preferenceRepository) {
        return new ManageUserPreferenceUseCase(preferenceRepository);
    }

    @Bean
    public NotificationHistoryUseCase notificationHistoryUseCase(NotificationRepository notificationRepository) {
        return new NotificationHistoryUseCase(notificationRepository);
    }

    @Bean
    public AuthenticateUserUseCase authenticateUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        return new AuthenticateUserUseCase(userRepository, passwordHasher);
    }

    @Bean
    public ScheduleNotificationUseCase scheduleNotificationUseCase(NotificationRepository notificationRepository) {
        return new ScheduleNotificationUseCase(notificationRepository);
    }

    @Bean
    public ManageNotificationTemplateUseCase manageNotificationTemplateUseCase(
            NotificationTemplateRepository templateRepository) {
        return new ManageNotificationTemplateUseCase(templateRepository);
    }

    @Bean
    public AdminDashboardUseCase adminDashboardUseCase(NotificationRepository notificationRepository,
                                                       UserRepository userRepository) {
        return new AdminDashboardUseCase(notificationRepository, userRepository);
    }

    @Bean
    public BroadcastNotificationUseCase broadcastNotificationUseCase(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            DeliverNotificationUseCase deliverNotificationUseCase) {
        return new BroadcastNotificationUseCase(
                notificationRepository, userRepository, deliverNotificationUseCase);
    }

    @Bean
    public com.uber.notification.application.usecase.MassEmailUseCase massEmailUseCase(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            UserPreferenceRepository preferenceRepository,
            DeliverNotificationUseCase deliverNotificationUseCase) {
        return new com.uber.notification.application.usecase.MassEmailUseCase(
                notificationRepository, userRepository, preferenceRepository, deliverNotificationUseCase);
    }
}
