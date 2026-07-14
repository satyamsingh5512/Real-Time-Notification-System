package com.uber.notification.application.event;

import com.uber.notification.domain.model.EventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Canonical envelope for every inbound business event, regardless of source topic.
 * Upstream services (Order, Payment, Social, Auth) publish messages that get mapped
 * to this shape by the Kafka listener adapters before reaching the application layer,
 * keeping use cases decoupled from Kafka/JSON wire formats.
 */
public record DomainEvent(
        String eventId,
        EventType eventType,
        UUID userId,
        Map<String, String> attributes,
        Instant occurredAt
) {
    public String attr(String key) {
        return attributes == null ? null : attributes.get(key);
    }
}
