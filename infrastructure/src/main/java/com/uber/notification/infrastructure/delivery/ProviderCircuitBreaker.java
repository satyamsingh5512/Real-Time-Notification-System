package com.uber.notification.infrastructure.delivery;

import com.uber.notification.application.port.DeliveryGuardPort;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.infrastructure.metrics.NotificationMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Per-channel circuit breaker guarding SES/Twilio/FCM calls.
 * Opens after {@code failureThreshold} consecutive retryable failures and stays
 * open for {@code openCooldown}; while open, delivery fails fast into the retry
 * path (no thread burned, no extra 429s against an already-throttling provider).
 * Permanent (non-retryable) failures never count toward the threshold — only
 * throttling/outage signals trip the breaker.
 */
@Component
public class ProviderCircuitBreaker implements DeliveryGuardPort {

    private final int failureThreshold;
    private final Duration openCooldown;
    private final NotificationMetrics metrics;
    private final Map<NotificationChannel, State> states = new EnumMap<>(NotificationChannel.class);

    public ProviderCircuitBreaker(
            @Value("${notification.delivery.circuit-breaker.failure-threshold:5}") int failureThreshold,
            @Value("${notification.delivery.circuit-breaker.open-cooldown-seconds:60}") long openCooldownSeconds,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationMetrics metrics) {
        this.failureThreshold = failureThreshold;
        this.openCooldown = Duration.ofSeconds(openCooldownSeconds);
        this.metrics = metrics;
    }

    @Override
    public synchronized boolean allow(NotificationChannel channel) {
        State state = states.computeIfAbsent(channel, c -> new State());
        if (!state.open) {
            return true;
        }
        if (Instant.now().isAfter(state.openedAt.plus(openCooldown))) {
            // Half-open: let one probe through; success closes, failure re-opens.
            state.open = false;
            state.consecutiveFailures = 0;
            return true;
        }
        return false;
    }

    @Override
    public synchronized void recordSuccess(NotificationChannel channel) {
        State state = states.computeIfAbsent(channel, c -> new State());
        state.consecutiveFailures = 0;
        state.open = false;
    }

    @Override
    public synchronized void recordFailure(NotificationChannel channel, boolean retryable) {
        if (!retryable) {
            return;
        }
        State state = states.computeIfAbsent(channel, c -> new State());
        state.consecutiveFailures++;
        if (!state.open && state.consecutiveFailures >= failureThreshold) {
            state.open = true;
            state.openedAt = Instant.now();
            if (metrics != null) {
                metrics.incrementCircuitOpened(channel.name());
            }
        }
    }

    private static final class State {
        int consecutiveFailures;
        boolean open;
        Instant openedAt;
    }
}
