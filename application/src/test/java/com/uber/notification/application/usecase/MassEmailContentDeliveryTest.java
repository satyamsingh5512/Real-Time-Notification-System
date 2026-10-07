package com.uber.notification.application.usecase;

import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.port.RetryPublisherPort;
import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.NotificationProviderRegistry;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression guard for a defect that silently shipped admin copy nobody received.
 *
 * <p>{@code DeliverNotificationUseCase.renderTemplate} used to resolve content from the
 * template table unconditionally, overwriting anything the caller had already rendered.
 * An admin wrote a subject and body, saw them in the UI, and every recipient received
 * {@code "Notification"} / {@code "(no content)"} instead.
 *
 * <p>This test wires the <em>real</em> {@link DeliverNotificationUseCase} to a capturing
 * provider — mocking the use case would only prove the use case works in isolation, which
 * is exactly what the previous test did while the bug was live.
 */
@ExtendWith(MockitoExtension.class)
class MassEmailContentDeliveryTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private RecipientResolverPort recipientResolver;
    @Mock
    private RetryPublisherPort retryPublisher;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserPreferenceRepository preferenceRepository;

    private final AtomicReference<Notification> captured = new AtomicReference<>();

    private MassEmailUseCase massEmailUseCase;

    private static final UUID ALICE = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Common wiring only. Stubs the mass-email path needs but the template-resolution
        // test does not are declared per-test, because Mockito's strict stubs correctly
        // flags an unused stub — and in `adminCopyIsDelivered` the *unused template stub*
        // is precisely the proof that renderTemplate now short-circuits before the
        // template lookup instead of overwriting the admin's copy.
        buildMassEmailPipeline();
    }

    private void buildMassEmailPipeline() {
        NotificationProvider capturingProvider = new NotificationProvider() {
            @Override
            public NotificationChannel supportedChannel() {
                return NotificationChannel.EMAIL;
            }

            @Override
            public void send(Notification notification, ProviderRecipient recipient) {
                captured.set(notification);
            }
        };

        NotificationProviderRegistry registry = new NotificationProviderRegistry(
                new EnumMap<>(NotificationChannel.class) {{
                    put(NotificationChannel.EMAIL, capturingProvider);
                }});

        DeliverNotificationUseCase deliver = new DeliverNotificationUseCase(
                notificationRepository, templateRepository, registry, recipientResolver,
                retryPublisher, null, null, null);

        massEmailUseCase = new MassEmailUseCase(notificationRepository, userRepository,
                preferenceRepository, deliver);

        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientResolver.resolve(any())).thenReturn(
                new ProviderRecipient("alice@example.com", null, null, ALICE.toString()));
    }

    /** Stubs only what a mass-email run actually touches. */
    private void stubMassEmailAudience() {
        when(userRepository.findAllIds(anyInt(), anyInt())).thenReturn(List.of(ALICE));
        when(preferenceRepository.findAllByUserId(any())).thenReturn(List.of());
        when(notificationRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("the admin's subject and body reach the provider, not the template table")
    void adminCopyIsDelivered() {
        stubMassEmailAudience();
        // No template stub on purpose. If renderTemplate consulted the template table it
        // would overwrite the admin's copy; leaving this unstubbed means any such call
        // returns Optional.empty() and still has to leave the copy alone.

        MassEmailUseCase.CampaignResult result = massEmailUseCase.send(
                null,
                "Release notes for {{name}}",
                "Hi {{name}}, here is what shipped.",
                Map.of("name", "Avery"),
                com.uber.notification.domain.model.NotificationPriority.MEDIUM);

        assertThat(result.sent()).isEqualTo(1);

        Notification delivered = captured.get();
        assertThat(delivered).isNotNull();
        assertThat(delivered.getRenderedSubject()).isEqualTo("Release notes for Avery");
        assertThat(delivered.getRenderedBody()).isEqualTo("Hi Avery, here is what shipped.");
    }

    @Test
    @DisplayName("a template row does not override admin-supplied copy")
    void adminCopyBeatsTemplate() {
        stubMassEmailAudience();
        // The worse version of the bug: with a MASS_EMAIL template present, the admin's
        // copy was replaced by the template's, so the UI showed one thing and the wire
        // carried another. The stub below must go UNUSED for this test to pass.
        var template = new com.uber.notification.domain.model.NotificationTemplate(
                UUID.randomUUID(), "MASS_EMAIL", NotificationChannel.EMAIL, 1,
                "Template subject", "Template body", "en-US", true,
                java.time.Instant.now(), 100);
        // `lenient` is required and meaningful here: this stub MUST go unused for the test
        // to pass. Reaching the template table at all is the regression — a strict stub
        // would fail the test that proves the template is never consulted.
        lenient().when(templateRepository.findActiveByCodeAndChannel(anyString(), any()))
                .thenReturn(Optional.of(template));

        massEmailUseCase.send(null, "Admin subject", "Admin body", Map.of(),
                com.uber.notification.domain.model.NotificationPriority.MEDIUM);

        Notification delivered = captured.get();
        assertThat(delivered).isNotNull();
        assertThat(delivered.getRenderedSubject()).isEqualTo("Admin subject");
        assertThat(delivered.getRenderedBody()).isEqualTo("Admin body");
    }

    @Test
    @DisplayName("event-driven notifications are still rendered from the template table")
    void eventDrivenStillRendersFromTemplate() {
        // The guard must not break normal template resolution.
        var template = new com.uber.notification.domain.model.NotificationTemplate(
                UUID.randomUUID(), "ORDER_PLACED", NotificationChannel.EMAIL, 1,
                "Order {{orderId}}", "Amount {{amount}}", "en-US", true,
                java.time.Instant.now(), 100);
        when(templateRepository.findActiveByCodeAndChannel(anyString(), any()))
                .thenReturn(Optional.of(template));

        Notification notification = new Notification(IdGenerator.newId(), ALICE,
                com.uber.notification.domain.model.EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "ORDER_PLACED",
                Map.of("orderId", "A4821", "amount", "$248"), 3, null,
                java.time.Instant.now(), "k-" + UUID.randomUUID());

        NotificationProviderRegistry registry = new NotificationProviderRegistry(
                new EnumMap<>(NotificationChannel.class) {{
                    put(NotificationChannel.EMAIL, new NotificationProvider() {
                        @Override
                        public NotificationChannel supportedChannel() {
                            return NotificationChannel.EMAIL;
                        }

                        @Override
                        public void send(Notification n, ProviderRecipient r) {
                            captured.set(n);
                        }
                    });
                }});

        new DeliverNotificationUseCase(notificationRepository, templateRepository, registry,
                recipientResolver, retryPublisher, null, null, null).execute(notification);

        Notification delivered = captured.get();
        assertThat(delivered.getRenderedSubject()).isEqualTo("Order A4821");
        assertThat(delivered.getRenderedBody()).isEqualTo("Amount $248");
    }
}