package com.uber.notification.infrastructure.provider.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Reads the upstream email service's remaining daily send budget.
 *
 * <p>The email service caps itself at 100 sends/day (hardcoded in its
 * {@code backend/src/middleware/rateLimit.js}) and rejects with HTTP 429 once exceeded.
 * Surfacing that budget lets an admin see, before a campaign runs, that it cannot fit —
 * rather than discovering it as a wave of silent failures halfway through.
 *
 * <p>Deliberately best-effort: a failure to read the quota must never block a campaign.
 * An unknown budget is reported as {@link Budget#unknown()} and the admin is told to
 * verify capacity rather than being given a false all-clear.
 */
@Component
@ConditionalOnProperty(prefix = "notification.email-service", name = "enabled", havingValue = "true")
public class EmailServiceQuotaClient {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceQuotaClient.class);

    private final RestClient client;

    public EmailServiceQuotaClient(RestClient emailServiceRestClient) {
        this.client = emailServiceRestClient;
    }

    /**
     * @param used       sends already made today, or {@code null} if unknown
     * @param limit      the daily cap, or {@code null} if unknown
     * @param reachable  whether the upstream answered at all
     */
    public record Budget(Integer used, Integer limit, boolean reachable) {

        public static Budget unknown() {
            return new Budget(null, null, false);
        }

        /**
         * A budget derived from local configuration when the upstream could not be asked.
         * The figure is an estimate, so {@code reachable} stays false and callers must
         * present it as "assumed", not "confirmed".
         */
        public static Budget assumed(int configuredLimit) {
            return configuredLimit > 0 ? new Budget(0, configuredLimit, false) : unknown();
        }

        public int remaining() {
            if (used == null || limit == null) {
                return -1;
            }
            return Math.max(0, limit - used);
        }

        /** True when we can prove the budget is already spent. */
        public boolean exhausted() {
            return limit != null && used != null && used >= limit;
        }
    }

    /**
     * The upstream returns {@code { used, limit, resetAt }} from {@code GET /api/quota}.
     * Field names are read defensively because they are outside our control and a rename
     * there must not throw inside an admin request.
     */
    public Budget fetchDailyBudget() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.get()
                    .uri("/api/quota")
                    .retrieve()
                    .body(Map.class);

            if (body == null) {
                return Budget.unknown();
            }
            Integer used = asInt(body.get("used"));
            Integer limit = asInt(body.get("limit"));
            if (used == null && limit == null) {
                return Budget.unknown();
            }
            return new Budget(used, limit, true);
        } catch (RestClientResponseException e) {
            log.warn("Email service quota read failed with {}: {}",
                    e.getStatusCode(), e.getStatusText());
            return Budget.unknown();
        } catch (Exception e) {
            log.warn("Email service quota read failed: {}", e.getMessage());
            return Budget.unknown();
        }
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}