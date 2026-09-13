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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Composition root for the application layer's use cases. Use cases are plain Java classes
 * with constructor-injected ports (no Spring annotations in the `application` module itself,
 * keeping it framework-agnostic), so they are wired up here as @Bean methods instead of
 * relying on classpath component scanning.
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public NotificationProviderRegistry notificationProviderRegistry(List<NotificationProvider> providers) {
        Map<NotificationChannel, NotificationProvider> byChannel = providers.stream()
                .collect(Collectors.toMap(NotificationProvider::supportedChannel, p -> p,
                        // Prefer mock providers when both real + mock are on the classpath
                        // (e.g. local profile overlap): mocks log instead of calling cloud APIs.
                        (a, b) -> a.getClass().getSimpleName().startsWith("Mock") ? a : b));
        return new NotificationProviderRegistry(byChannel);
    }

    @Bean
    public ProcessIncomingEventUseCase processIncomingEventUseCase(
            NotificationRepository notificationRepository, UserPreferenceRepository preferenceRepository) {
        return new ProcessIncomingEventUseCase(notificationRepository, preferenceRepository);
    }

    @Bean
    public DeliverNotificationUseCase deliverNotificationUseCase(
            NotificationRepository notificationRepository,
            NotificationTemplateRepository templateRepository,
            NotificationProviderRegistry providerRegistry,
            RecipientResolverPort recipientResolver,
            RetryPublisherPort retryPublisher,
            // Optional: present in the full app (Micrometer adapter), absent in persistence-only test slice.
            @org.springframework.beans.factory.annotation.Autowired(required = false) DeliveryMetricsPort deliveryMetrics) {
        return new DeliverNotificationUseCase(notificationRepository, templateRepository,
                providerRegistry, recipientResolver, retryPublisher,
                deliveryMetrics == null ? DeliveryMetricsPort.NOOP : deliveryMetrics);
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
}
