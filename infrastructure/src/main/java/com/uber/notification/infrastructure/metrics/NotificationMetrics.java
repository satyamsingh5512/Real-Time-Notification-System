package com.uber.notification.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for custom business metrics exposed via /actuator/prometheus:
 * <ul>
 *   <li>notifications.dispatched.total (tags: channel, eventType, status)</li>
 *   <li>notification.delivery.latency (tags: channel) — third-party call latency</li>
 *   <li>websocket.sessions.active — gauge of live WebSocket connections on this pod</li>
 *   <li>kafka.events.consumed.total (tags: eventType)</li>
 *   <li>notifications.dlq.total (tags: reason)</li>
 * </ul>
 */
@Component
public class NotificationMetrics {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger activeWebSocketSessions = new AtomicInteger(0);

    public NotificationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("websocket.sessions.active", activeWebSocketSessions, AtomicInteger::get)
                .description("Active WebSocket connections on this pod")
                .register(meterRegistry);
    }

    public void incrementDispatched(String channel, String eventType, String status) {
        Counter.builder("notifications.dispatched.total")
                .description("Total notifications dispatched")
                .tags("channel", channel, "eventType", eventType, "status", status)
                .register(meterRegistry)
                .increment();
    }

    public void recordDeliveryLatency(String channel, Duration latency) {
        Timer.builder("notification.delivery.latency")
                .description("Latency of third-party provider calls")
                .tags("channel", channel)
                .register(meterRegistry)
                .record(latency);
    }

    public void incrementKafkaConsumed(String eventType) {
        Counter.builder("kafka.events.consumed.total")
                .description("Total Kafka business events consumed")
                .tags("eventType", eventType)
                .register(meterRegistry)
                .increment();
    }

    public void incrementDlq(String reason) {
        Counter.builder("notifications.dlq.total")
                .description("Total notifications routed to the dead-letter topic")
                .tags("reason", reason == null ? "unknown" : reason)
                .register(meterRegistry)
                .increment();
    }

    public void incrementPoisonPill(String topic) {
        Counter.builder("kafka.events.poison-pill.total")
                .description("Total malformed Kafka payloads dead-lettered")
                .tags("topic", topic == null ? "unknown" : topic)
                .register(meterRegistry)
                .increment();
    }

    public void incrementRetryScheduled(String bucketTopic) {
        Counter.builder("notifications.retry.scheduled.total")
                .description("Total retries scheduled")
                .tags("bucket", bucketTopic)
                .register(meterRegistry)
                .increment();
    }

    // WebSocket gauge helpers — called by WebSocketSessionRegistry.
    public void onSessionOpened() {
        activeWebSocketSessions.incrementAndGet();
    }

    public void onSessionClosed() {
        activeWebSocketSessions.updateAndGet(v -> Math.max(0, v - 1));
    }

    public int getActiveWebSocketSessions() {
        return activeWebSocketSessions.get();
    }
}
