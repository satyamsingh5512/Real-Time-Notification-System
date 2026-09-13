package com.uber.notification.infrastructure.kafka.producer;

import com.uber.notification.infrastructure.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes unparseable inbound event payloads (poison pills) to
 * {@code events.poison-pill} for later inspection/replay instead of silently dropping them.
 */
@Component
public class PoisonPillPublisher {

    private static final Logger log = LoggerFactory.getLogger(PoisonPillPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PoisonPillPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void quarantine(String sourceTopic, String rawPayload, String error) {
        try {
            String quarantined = "{\"sourceTopic\":\"" + sourceTopic + "\",\"error\":\""
                    + error.replace("\"", "'") + "\",\"payload\":" + quoted(rawPayload) + "}";
            kafkaTemplate.send(KafkaTopics.EVENTS_POISON_PILL, quarantined);
            log.warn("Quarantined poison pill from topic {} to {}", sourceTopic, KafkaTopics.EVENTS_POISON_PILL);
        } catch (Exception e) {
            log.error("Failed to quarantine poison pill from topic {}", sourceTopic, e);
        }
    }

    private static String quoted(String raw) {
        if (raw == null) {
            return "null";
        }
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
