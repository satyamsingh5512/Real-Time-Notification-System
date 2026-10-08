package com.uber.notification.application.validation;

import com.uber.notification.domain.model.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventMessageValidatorTest {

    @Test
    void acceptsAWellFormedOrderEvent() {
        var result = EventMessageValidator.validate("evt-1", EventType.ORDER_PLACED,
                "3f1c2b4a-1111-2222-3333-444455556666",
                Map.of("orderId", "A1", "amount", "10.00"), Instant.now());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsMissingEventId() {
        var result = EventMessageValidator.validate(null, EventType.ORDER_PLACED,
                "3f1c2b4a-1111-2222-3333-444455556666",
                Map.of("orderId", "A1", "amount", "10.00"), Instant.now());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("eventId is required");
    }

    @Test
    void rejectsNonUuidUserId() {
        var result = EventMessageValidator.validate("evt-1", EventType.ORDER_PLACED, "not-a-uuid",
                Map.of("orderId", "A1", "amount", "10.00"), Instant.now());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("userId must be a UUID");
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        var result = EventMessageValidator.validate("evt-1", EventType.ORDER_PLACED,
                "3f1c2b4a-1111-2222-3333-444455556666",
                Map.of("orderId", "A1"), Instant.now()); // amount missing

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("attributes.amount"));
    }

    @Test
    void rejectsBlankAttributeValues() {
        var result = EventMessageValidator.validate("evt-1", EventType.OTP_GENERATED,
                "3f1c2b4a-1111-2222-3333-444455556666",
                Map.of("otp", "  "), Instant.now());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("attributes.otp"));
    }

    @Test
    void rejectsFarFutureTimestamps() {
        var result = EventMessageValidator.validate("evt-1", EventType.LIKE_RECEIVED,
                "3f1c2b4a-1111-2222-3333-444455556666",
                Map.of("actorName", "Ada"), Instant.now().plusSeconds(3600));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("occurredAt"));
    }

    @Test
    void collectsEveryProblemInOnePass() {
        var result = EventMessageValidator.validate("", EventType.MENTIONED, "nope",
                Map.of(), Instant.now());

        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(3);
    }
}