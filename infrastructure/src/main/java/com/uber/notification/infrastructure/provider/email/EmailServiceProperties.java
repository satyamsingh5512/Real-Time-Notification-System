package com.uber.notification.infrastructure.provider.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the external email-service microservice
 * (github.com/satyamsingh5512/email-service), used as the EMAIL delivery transport.
 *
 * <p>Enabled with {@code notification.email-service.enabled=true}. When disabled the
 * EMAIL channel is served by whichever other provider is registered (AWS SES by
 * default), so this is a drop-in replacement rather than an additional requirement.
 *
 * <p><b>Known limitation of the upstream service:</b> it enforces a hardcoded
 * 100-sends-per-day quota per user (see its {@code backend/src/middleware/rateLimit.js}).
 * {@code mass-mail-enabled} defaults to <b>false</b> for that reason — a real campaign
 * exceeds 100/day immediately. See {@code docs/EMAIL_SERVICE_INTEGRATION.md}.
 */
@ConfigurationProperties(prefix = "notification.email-service")
public class EmailServiceProperties {

    /** Absolute base URL of the email service, e.g. {@code http://email-service:3001}. */
    private String baseUrl = "";

    /**
     * Optional bearer token. The upstream service currently ignores authentication
     * entirely (its {@code authenticate} middleware is a bypass), but the header is sent
     * so this integration keeps working when that is fixed rather than needing a change.
     */
    private String apiKey = "";

    /** Per-request connect timeout. */
    private int connectTimeoutMs = 3_000;

    /** Per-request read timeout. Generous, because bulk recipient scans are slow. */
    private int readTimeoutMs = 10_000;

    /** Master switch for using this service as the EMAIL transport. */
    private boolean enabled = false;

    /**
     * Whether admin mass-email campaigns may be dispatched through this platform.
     * Defaults to false: mass sending is high-abuse-risk and the upstream daily quota
     * makes it non-functional at scale until the limit is raised there.
     */
    private boolean massMailEnabled = false;

    /**
     * Refuse a campaign whose estimated recipient count exceeds this. A backstop so a
     * mistyped "send to everyone" cannot take out the day's quota for everyone else.
     * Set to 0 to disable the guard (not recommended).
     */
    private int massMailMaxRecipients = 100;

    /**
     * Expected daily send budget. Used as the fallback ceiling when the upstream quota
     * endpoint cannot be reached, so the pre-flight warning still fires instead of the
     * admin being given a false all-clear. Mirrors the upstream constant, which is
     * hardcoded at 100 in its {@code backend/src/middleware/rateLimit.js}.
     */
    private int dailyQuota = 100;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMassMailEnabled() {
        return massMailEnabled;
    }

    public void setMassMailEnabled(boolean massMailEnabled) {
        this.massMailEnabled = massMailEnabled;
    }

    public int getMassMailMaxRecipients() {
        return massMailMaxRecipients;
    }

    public void setMassMailMaxRecipients(int massMailMaxRecipients) {
        this.massMailMaxRecipients = massMailMaxRecipients;
    }

    public int getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(int dailyQuota) {
        this.dailyQuota = dailyQuota;
    }
}