package com.uber.notification.infrastructure.metrics;

import com.uber.notification.application.port.DeliveryMetricsPort;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Micrometer-backed implementation of the application's delivery-metrics port. */
@Component
public class MicrometerDeliveryMetricsAdapter implements DeliveryMetricsPort {

    private final NotificationMetrics notificationMetrics;

    public MicrometerDeliveryMetricsAdapter(NotificationMetrics notificationMetrics) {
        this.notificationMetrics = notificationMetrics;
    }

    @Override
    public void onDispatched(String channel, String eventType, String status) {
        notificationMetrics.incrementDispatched(channel, eventType, status);
    }

    @Override
    public void recordDeliveryLatency(String channel, Duration latency) {
        notificationMetrics.recordDeliveryLatency(channel, latency);
    }
}
