package com.uber.notification.infrastructure.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Wires {@link KafkaConsumerLagMetrics} to the real Micrometer registry and to the
 * Spring-managed {@link KafkaAdmin}, so lag gauges land in /actuator/prometheus.
 */
@Configuration
public class KafkaLagMetricsConfig {

    @Bean
    public KafkaConsumerLagMetrics kafkaConsumerLagMetrics(
            org.springframework.beans.factory.ObjectProvider<KafkaAdmin> kafkaAdmin,
            MeterRegistry meterRegistry,
            @Value("${notification.kafka.lag.monitored-groups:notification-platform-events}")
            String monitoredGroups) {
        KafkaConsumerLagMetrics metrics = new KafkaConsumerLagMetrics(kafkaAdmin, monitoredGroups);
        metrics.bind(meterRegistry);
        return metrics;
    }
}