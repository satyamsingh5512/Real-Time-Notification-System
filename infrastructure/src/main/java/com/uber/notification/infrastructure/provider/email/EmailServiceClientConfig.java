package com.uber.notification.infrastructure.provider.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.Assert;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Wires the HTTP client used to talk to the external email service.
 *
 * <p>Uses the JDK's built-in {@link HttpClient} via Spring's
 * {@link JdkClientHttpRequestFactory} rather than adding Apache HttpClient or OkHttp —
 * the platform already ships on the JDK, so this keeps the dependency footprint
 * unchanged.
 *
 * <p>Only active when {@code notification.email-service.enabled=true}.
 */
@Configuration
@EnableConfigurationProperties(EmailServiceProperties.class)
@ConditionalOnProperty(prefix = "notification.email-service", name = "enabled", havingValue = "true")
public class EmailServiceClientConfig {

    /**
     * Explicit timeouts. The JDK client defaults to infinite on both connect and read,
     * which would let one slow upstream response pin a delivery worker indefinitely —
     * and because {@link com.uber.notification.domain.model.Notification} delivery is
     * synchronous, that would exhaust the request pool.
     */
    @Bean
    public RestClient emailServiceRestClient(EmailServiceProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                // Follow no redirects: the upstream is an internal service and a redirect
                // to an external host would leak the Authorization header.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));

        String baseUrl = normaliseBaseUrl(properties.getBaseUrl());
        // Fail at boot rather than on the first send. With a blank base URL the request
        // URI stays relative and RestClient rejects it per call, which would surface as a
        // campaign full of delivery exceptions instead of a clear configuration error.
        Assert.hasText(baseUrl,
                "notification.email-service.base-url must be set when the email service is enabled");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);

        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getApiKey());
        }

        return builder.build();
    }

    /**
     * RestClient rejects a base URL with a trailing slash in a way that produces a
     * doubled separator when combined with a path starting with "/".
     */
    private static String normaliseBaseUrl(String baseUrl) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}