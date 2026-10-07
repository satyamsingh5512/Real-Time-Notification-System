package com.uber.notification.application.validation;

import com.uber.notification.domain.model.EventType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared JSON-schema equivalent for all 8 inbound Kafka topics: validates the wire
 * shape <em>before</em> fan-out so malformed payloads are quarantined as poison
 * pills with a precise reason instead of failing halfway through persistence.
 * Per-event required attributes mirror the load-test contract
 * ({@code load-testing/kafka_event_producer_load_test.py}).
 */
public final class EventMessageValidator {

    private static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

    private EventMessageValidator() {
    }

    public record ValidationResult(boolean valid, List<String> errors) {
        public static ValidationResult ok() {
            return new ValidationResult(true, List.of());
        }
    }

    public static ValidationResult validate(String eventId, EventType eventType, String userId,
                                            Map<String, String> attributes, Instant occurredAt) {
        List<String> errors = new ArrayList<>();
        if (eventId == null || eventId.isBlank()) {
            errors.add("eventId is required");
        }
        if (eventType == null) {
            errors.add("eventType is required");
            return new ValidationResult(false, errors);
        }
        if (userId == null || userId.isBlank()) {
            errors.add("userId is required");
        } else {
            try {
                UUID.fromString(userId);
            } catch (IllegalArgumentException e) {
                errors.add("userId must be a UUID");
            }
        }
        Map<String, String> attrs = attributes != null ? attributes : Map.of();
        for (String required : requiredAttributes(eventType)) {
            String v = attrs.get(required);
            if (v == null || v.isBlank()) {
                errors.add("attributes." + required + " is required for " + eventType);
            }
        }
        if (occurredAt != null && occurredAt.isAfter(Instant.now().plus(MAX_CLOCK_SKEW))) {
            errors.add("occurredAt is more than 5 minutes in the future");
        }
        return errors.isEmpty() ? ValidationResult.ok() : new ValidationResult(false, List.copyOf(errors));
    }

    private static List<String> requiredAttributes(EventType eventType) {
        return switch (eventType) {
            case ORDER_PLACED, PAYMENT_SUCCESS -> List.of("orderId", "amount");
            case ORDER_DELIVERED -> List.of("orderId");
            case COMMENT_ADDED -> List.of("actorName", "commentText");
            case LIKE_RECEIVED, MENTIONED -> List.of("actorName");
            case PASSWORD_RESET -> List.of("resetLink");
            case OTP_GENERATED -> List.of("otp");
            default -> List.of();
        };
    }
}
