package com.uber.notification.infrastructure.provider.email;

import com.uber.notification.application.provider.NotificationProvider;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.NotificationDeliveryException;
import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delivers the EMAIL channel through the external email-service microservice
 * (github.com/satyamsingh5512/email-service) instead of AWS SES.
 *
 * <p>This is a {@link NotificationProvider} strategy implementation, so every existing
 * guarantee of the delivery pipeline keeps applying unchanged: the per-channel circuit
 * breaker, retry topics, dead-letter routing, delivery metrics, digest batching, quiet
 * hours and per-user opt-in are all enforced <em>above</em> this class. Mass campaigns
 * therefore fan out through {@code DeliverNotificationUseCase} one recipient at a time
 * rather than being handed to the upstream bulk endpoint — a single bulk POST would
 * bypass every one of those controls.
 *
 * <p>Mapping to the upstream contract, {@code POST /api/email/send}:
 * <ul>
 *   <li>{@code to} — the recipient address from {@link ProviderRecipient#email()}</li>
 *   <li>{@code from} — optional; the upstream validates it against its own
 *       {@code ALLOWED_FROM_EMAILS} allow-list and falls back to its default</li>
 *   <li>{@code subject} / {@code body} — already rendered by the platform's
 *       {@code TemplateRenderer}; this class never re-renders</li>
 *   <li>{@code category} — {@code general} for platform traffic, {@code events} for
 *       event-driven mail, {@code support} for password resets and OTPs</li>
 *   <li>{@code priority} — derived from the notification's own priority</li>
 * </ul>
 *
 * <p>Retry semantics are the load-bearing part of this class. The upstream returns 4xx
 * for a bad address or an exceeded quota and 5xx for an infrastructure fault; those must
 * map to {@code retryable=false} and {@code retryable=true} respectively, or the platform
 * will either retry a permanent failure forever or drop a transient one on the floor.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "notification.email-service", name = "enabled", havingValue = "true")
public class EmailServiceProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceProvider.class);

    private final RestClient client;

    public EmailServiceProvider(RestClient emailServiceRestClient) {
        this.client = emailServiceRestClient;
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(Notification notification, ProviderRecipient recipient) {
        String address = recipient == null ? null : recipient.email();
        if (address == null || address.isBlank()) {
            // Permanent: retrying cannot conjure an address that does not exist.
            throw new NotificationDeliveryException(
                    "Recipient has no email address on file (user " + notification.getUserId() + ")",
                    false);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        // `from` is deliberately absent rather than null. The upstream schema is
        // `from: z.string().email().optional()`, which accepts `undefined` but not an
        // explicit `null`, so including the key as null produces a permanent 400. Omitting
        // it lets the upstream apply its own allow-listed default.
        payload.put("to", address);
        // The upstream schema requires a non-empty subject; a template with no subject
        // template yields null here, and the platform's own fallback is used instead.
        payload.put("subject", subjectFor(notification));
        payload.put("body", bodyFor(notification));
        payload.put("category", categoryFor(notification));
        payload.put("priority", priorityFor(notification));

        try {
            client.post()
                    .uri("/api/email/send")
                    .body(payload)
                    .retrieve()
                    // A 2xx is a success; we do not need the body.
                    .toBodilessEntity();

            log.debug("Email accepted by email-service: notificationId={} to={}",
                    notification.getId(), address);

        } catch (RestClientResponseException e) {
            throw translate(e, notification, address);
        } catch (ResourceAccessException e) {
            // Connect timeout, read timeout, DNS failure, connection refused: all transient
            // from our point of view, so the platform retries with backoff.
            throw new NotificationDeliveryException(
                    "Email service unreachable for notification " + notification.getId()
                            + ": " + rootMessage(e),
                    true, e);
        } catch (Exception e) {
            // Unexpected (serialisation, etc.). Treated as retryable so a transient fault
            // is not silently lost; the circuit breaker stops this becoming a hot loop.
            throw new NotificationDeliveryException(
                    "Unexpected email service failure for notification " + notification.getId()
                            + ": " + rootMessage(e),
                    true, e);
        }
    }

    /**
     * Decides retryability from the upstream status code.
     *
     * <p>Deliberately a whitelist rather than "retry everything except 400": an
     * unrecognised 4xx from a future upstream version must not be retried forever, but a
     * 408/429 genuinely is transient and is listed explicitly.
     */
    private NotificationDeliveryException translate(RestClientResponseException e,
                                                     Notification notification,
                                                     String address) {
        HttpStatusCode status = e.getStatusCode();
        int code = status.value();

        // Never echo the recipient address into a log line or an exception message that
        // may surface in the admin UI; identify the notification instead.
        String message = "Email service returned " + code + " for notification "
                + notification.getId() + ": " + summariseBody(e);

        if (code == 408) {
            // Request timeout: genuinely transient.
            return new NotificationDeliveryException(message, true, e);
        }
        if (code >= 400 && code < 500) {
            // 400 invalid request, 422 schema violation, and notably 429 — the upstream's
            // daily-quota rejection. Treating 429 as retryable burned the whole retry
            // budget on a condition that cannot resolve before midnight UTC, then
            // dead-lettered; permanent sends the failure straight to the record where an
            // operator can see why. MassEmailController also refuses the campaign
            // up-front when the budget is already spent, so this is the backstop.
            return new NotificationDeliveryException(message, false, e);
        }
        // 5xx and anything unexpected from the upstream: infrastructure fault.
        return new NotificationDeliveryException(message, true, e);
    }

    private String subjectFor(Notification notification) {
        String subject = notification.getRenderedSubject();
        return subject == null || subject.isBlank() ? "Notification" : subject;
    }

    private String bodyFor(Notification notification) {
        String body = notification.getRenderedBody();
        // The upstream schema requires a non-empty body; an empty string would fail
        // validation with a 400 and be treated as permanent, which is a confusing way to
        // learn that a template rendered to nothing.
        return body == null || body.isBlank() ? "(no content)" : body;
    }

    /**
     * Maps the platform's domain categories onto the upstream's fixed enum. The upstream
     * accepts only general | aws | events | newsletter | support, so anything unrecognised
     * must fall back to {@code general} rather than fail validation.
     */
    private String categoryFor(Notification notification) {
        return switch (notification.getEventType()) {
            case PASSWORD_RESET, OTP_GENERATED -> "support";
            case BROADCAST_ANNOUNCEMENT -> "newsletter";
            default -> "events";
        };
    }

    private String priorityFor(Notification notification) {
        return switch (notification.getPriority()) {
            case HIGH -> "urgent";
            default -> "normal";
        };
    }

    /** Keeps exception messages bounded; upstream error bodies can be large. */
    private String summariseBody(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getStatusText();
        }
        String trimmed = body.strip();
        return trimmed.length() > 300 ? trimmed.substring(0, 300) + "…" : trimmed;
    }

    private String rootMessage(Throwable e) {
        Throwable cursor = e;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
    }
}