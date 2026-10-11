package com.uber.notification.infrastructure.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumer-lag gauge used for scaling and alerting (Uber/Kafka production pattern):
 * {@code kafka.consumer.lag{group=...}} plus {@code kafka.consumer.lag.total}, so a
 * dashboard or an autoscaler can react to a backing-up pipeline instead of waiting
 * for user-visible lag.
 *
 * <p>Sampled on a schedule through the Kafka {@link AdminClient} rather than from the
 * consumer thread, so it adds no work to the hot ingest path and is safe to leave on
 * in any environment. Failures are swallowed: observability must never break delivery.
 */
public class KafkaConsumerLagMetrics {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagMetrics.class);

    private final ObjectProvider<KafkaAdmin> kafkaAdminProvider;
    private final List<String> monitoredGroups;
    private final Map<String, AtomicLong> lagByGroup = new HashMap<>();
    private final AtomicLong totalLag = new AtomicLong(0);

    public KafkaConsumerLagMetrics(ObjectProvider<KafkaAdmin> kafkaAdminProvider,
                                   @Value("${notification.kafka.lag.monitored-groups:notification-platform-events}")
                                   String monitoredGroupsCsv) {
        this.kafkaAdminProvider = kafkaAdminProvider;
        this.monitoredGroups = List.of(monitoredGroupsCsv.split(","));
        Gauge.builder("kafka.consumer.lag.total", totalLag, AtomicLong::get)
                .description("Total consumer lag across monitored consumer groups")
                .register(resolveRegistry());
        for (String group : this.monitoredGroups) {
            String g = group.trim();
            if (!g.isEmpty()) {
                AtomicLong holder = lagByGroup.computeIfAbsent(g, k -> new AtomicLong(0));
                Gauge.builder("kafka.consumer.lag", holder, AtomicLong::get)
                        .description("Consumer lag for a group")
                        .tag("group", g)
                        .register(resolveRegistry());
            }
        }
    }

    private io.micrometer.core.instrument.MeterRegistry resolveRegistry() {
        // GaugeBuilder needs a registry; KafkaAdmin-based bean is constructed before
        // the registry may be injected, so use the composite holder below instead.
        return registryHolder;
    }

    private volatile io.micrometer.core.instrument.MeterRegistry registryHolder =
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    /** Called by Spring once the real registry is available (see KafkaLagMetricsConfig). */
    public void bind(MeterRegistry meterRegistry) {
        this.registryHolder = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${notification.kafka.lag.poll-interval-ms:30000}")
    public void refresh() {
        KafkaAdmin admin = kafkaAdminProvider.getIfAvailable();
        if (admin == null) {
            return;
        }
        try (AdminClient adminClient = AdminClient.create(admin.getConfigurationProperties())) {
            long summed = 0;
            for (String group : monitoredGroups) {
                String g = group.trim();
                if (g.isEmpty()) {
                    continue;
                }
                long lag = computeLag(adminClient, g);
                AtomicLong holder = lagByGroup.get(g);
                if (holder != null) {
                    holder.set(lag);
                }
                summed += lag;
            }
            totalLag.set(summed);
        } catch (Exception e) {
            log.debug("consumer lag refresh skipped: {}", e.getMessage());
        }
    }

    private long computeLag(AdminClient adminClient, String group) {
        try {
            Map<TopicPartition, OffsetAndMetadata> committed =
                    adminClient.listConsumerGroupOffsets(group)
                            .partitionsToOffsetAndMetadata()
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
            if (committed.isEmpty()) {
                return 0;
            }
            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(committed.keySet().stream()
                                    .collect(java.util.stream.Collectors.toMap(tp -> tp,
                                            tp -> OffsetSpec.latest())))
                            .all()
                            .get(5, java.util.concurrent.TimeUnit.SECONDS);
            long lag = 0;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : committed.entrySet()) {
                var end = endOffsets.get(entry.getKey());
                if (end != null && end.offset() >= 0) {
                    lag += Math.max(0, end.offset() - entry.getValue().offset());
                }
            }
            return lag;
        } catch (Exception e) {
            log.debug("lag for group {} unavailable: {}", group, e.getMessage());
            return 0;
        }
    }
}