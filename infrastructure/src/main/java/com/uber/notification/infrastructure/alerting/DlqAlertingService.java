package com.uber.notification.infrastructure.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Outbound alerting for permanently-failed notifications (DLQ).
 * Supports a generic HTTP webhook (Slack incoming webhook, PagerDuty Events API v2,
 * or any custom on-call endpoint). Disabled when no webhook URL is configured —
 * the DLQ consumer then degrades gracefully to log + metrics only.
 */
@Service
public class DlqAlertingService {

    private static final Logger log = LoggerFactory.getLogger(DlqAlertingService.class);

    private final String webhookUrl;
    private final RestTemplate restTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public DlqAlertingService(
            @Value("${notification.alerting.webhook-url:}") String webhookUrl) {
        this(webhookUrl, new RestTemplate());
    }

    // Visible for testing — inject a mock RestTemplate to assert webhook delivery.
    DlqAlertingService(String webhookUrl, RestTemplate restTemplate) {
        this.webhookUrl = webhookUrl == null ? "" : webhookUrl.trim();
        this.restTemplate = restTemplate;
    }

    public boolean isEnabled() {
        return !webhookUrl.isBlank();
    }

    public void alertDeadLetter(java.util.UUID notificationId, int attempts, String reason) {
        if (!isEnabled()) {
            return;
        }
        String text = ":rotating_light: RTNS DLQ — notification=%s attempts=%d reason=%s".formatted(
                notificationId, attempts, reason);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // Slack-compatible payload; PagerDuty/generic endpoints typically accept/ignore extra fields.
            Map<String, Object> payload = Map.of("text", text);
            restTemplate.postForEntity(webhookUrl, new HttpEntity<>(payload, headers), String.class);
        } catch (Exception e) {
            // Alerting must never break the DLQ consumer.
            log.warn("Failed to deliver DLQ webhook alert for notification {}", notificationId, e);
        }
    }
}
