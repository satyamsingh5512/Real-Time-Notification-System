package com.uber.notification.infrastructure.provider.email;

import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The load-bearing behaviour of {@link EmailServiceProvider} is its retryable/permanent
 * classification. Getting it wrong either retries a permanent failure until the
 * dead-letter queue fills, or silently drops a transient one.
 */
class EmailServiceProviderTest {

    private static final UUID NOTIFICATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private MockRestServiceServer server;
    private EmailServiceProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://email-service:3001");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new EmailServiceProvider(builder.build());
    }

    private static Notification notification(EventType eventType, NotificationPriority priority) {
        Notification n = new Notification(NOTIFICATION_ID, USER_ID, eventType,
                NotificationChannel.EMAIL, "TPL", Map.of(), 3, null, Instant.now(), "k-" + UUID.randomUUID());
        n.setPriority(priority);
        n.markRendered("Subject line", "Body text");
        return n;
    }

    private static ProviderRecipient recipient(String email) {
        return new ProviderRecipient(email, null, null, USER_ID.toString());
    }

    @Test
    @DisplayName("posts the rendered subject and body to /api/email/send")
    void sendsThroughUpstream() {
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.to").value("user@example.com"))
                .andExpect(jsonPath("$.subject").value("Subject line"))
                .andExpect(jsonPath("$.body").value("Body text"))
                .andRespond(withSuccess("{\"message\":\"sent\"}", MediaType.APPLICATION_JSON));

        provider.send(notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("user@example.com"));

        server.verify();
    }

    @Test
    @DisplayName("category is mapped onto the upstream's fixed enum")
    void mapsCategory() {
        // A value outside {general, aws, events, newsletter, support} would fail the
        // upstream's zod validation with a 400 and be treated as permanent, so the
        // mapping must never emit something else.
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andExpect(jsonPath("$.category").value("support"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.send(notification(EventType.PASSWORD_RESET, NotificationPriority.HIGH),
                recipient("user@example.com"));
        server.verify();
    }

    @Test
    @DisplayName("4xx is permanent — must not be retried")
    void clientErrorIsPermanent() {
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"bad request\"}"));

        assertThatThrownBy(() -> provider.send(
                notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("user@example.com")))
                .isInstanceOf(NotificationDeliveryException.class)
                .matches(e -> !((NotificationDeliveryException) e).isRetryable(),
                        "a 400 will not improve on retry");
    }

    @Test
    @DisplayName("429 from the upstream daily quota is permanent, not retried")
    void rateLimitedIsPermanent() {
        // The upstream rejects with 429 once its 100/day quota is spent. Retrying within
        // the same campaign would just burn the retry budget for the same outcome.
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("quota exceeded"));

        assertThatThrownBy(() -> provider.send(
                notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("user@example.com")))
                .isInstanceOf(NotificationDeliveryException.class)
                .matches(e -> !((NotificationDeliveryException) e).isRetryable());
    }

    @Test
    @DisplayName("5xx is transient — must be retried")
    void serverErrorIsRetryable() {
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("boom"));

        assertThatThrownBy(() -> provider.send(
                notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("user@example.com")))
                .isInstanceOf(NotificationDeliveryException.class)
                .matches(e -> ((NotificationDeliveryException) e).isRetryable());
    }

    @Test
    @DisplayName("a missing address fails locally without calling the upstream")
    void blankAddressFailsFast() {
        assertThatThrownBy(() -> provider.send(
                notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("  ")))
                .isInstanceOf(NotificationDeliveryException.class)
                .matches(e -> !((NotificationDeliveryException) e).isRetryable());

        server.verify();
    }

    @Test
    @DisplayName("a null subject is replaced — the upstream requires a non-empty one")
    void blankSubjectIsSubstituted() {
        Notification n = new Notification(NOTIFICATION_ID, USER_ID, EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "TPL", Map.of(), 3, null, Instant.now(), "k");
        n.markRendered(null, "Body");

        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andExpect(jsonPath("$.subject").value("Notification"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.send(n, recipient("user@example.com"));
        server.verify();
    }

    @Test
    @DisplayName("an empty body is substituted rather than sent as blank")
    void blankBodyIsSubstituted() {
        Notification n = new Notification(NOTIFICATION_ID, USER_ID, EventType.ORDER_PLACED,
                NotificationChannel.EMAIL, "TPL", Map.of(), 3, null, Instant.now(), "k");
        n.markRendered("Subject", "");

        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("(no content)")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        provider.send(n, recipient("user@example.com"));
        server.verify();
    }

    @Test
    @DisplayName("the recipient address is never echoed into the exception message")
    void doesNotLeakAddress() {
        server.expect(requestTo("http://email-service:3001/api/email/send"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"error\":\"nope\"}"));

        assertThatThrownBy(() -> provider.send(
                notification(EventType.ORDER_PLACED, NotificationPriority.MEDIUM),
                recipient("private.person@example.com")))
                .hasMessageNotContaining("private.person@example.com")
                .hasMessageContaining(NOTIFICATION_ID.toString().substring(0, 8));
    }

    @Test
    void reportsEmailChannel() {
        assertThat(provider.supportedChannel()).isEqualTo(NotificationChannel.EMAIL);
    }
}