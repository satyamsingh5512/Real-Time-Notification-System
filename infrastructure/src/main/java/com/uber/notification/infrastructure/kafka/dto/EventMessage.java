package com.uber.notification.infrastructure.kafka.dto;

import com.uber.notification.domain.model.EventType;

import java.time.Instant;
import java.util.Map;

/**
 * Wire format (JSON) for all inbound business events. All 8 event topics share this shape;
 * per-event-specific fields travel in {@code attributes} and get consumed by
 * {@code TemplateRenderer} using {{placeholder}} keys, e.g. {{orderId}}, {{amount}}, {{otp}}.
 */
public record EventMessage(
        String eventId,
        EventType eventType,
        String userId,
        Map<String, String> attributes,
        Instant occurredAt
) {
}
