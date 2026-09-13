package com.uber.notification.application.port;

import java.time.Duration;

/**
 * Output port for delivery observability. The application layer reports
 * dispatch outcomes through this port; the infrastructure adapter backs it
 * with Micrometer (Prometheus). A no-op default keeps unit tests simple.
 */
public interface DeliveryMetricsPort {

    void onDispatched(String channel, String eventType, String status);

    void recordDeliveryLatency(String channel, Duration latency);

    DeliveryMetricsPort NOOP = new DeliveryMetricsPort() {
        @Override
        public void onDispatched(String channel, String eventType, String status) {
        }

        @Override
        public void recordDeliveryLatency(String channel, Duration latency) {
        }
    };
}
