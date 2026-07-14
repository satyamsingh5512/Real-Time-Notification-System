package com.uber.notification.application.template;

import com.uber.notification.domain.model.NotificationChannel;

import java.util.List;
import java.util.Map;

import static com.uber.notification.domain.model.EventType.*;
import static com.uber.notification.domain.model.NotificationChannel.*;

/**
 * Static routing table: which channels a given business event fans out to by default,
 * before per-user preference filtering is applied. Kept as code (not DB config) because
 * it represents product-level notification policy that changes with a deploy/review,
 * whereas per-user opt-outs (UserPreference) are runtime data.
 */
public final class EventChannelRouting {

    private static final Map<com.uber.notification.domain.model.EventType, List<NotificationChannel>> ROUTES = Map.of(
            ORDER_PLACED, List.of(EMAIL, PUSH, IN_APP),
            ORDER_DELIVERED, List.of(PUSH, IN_APP, SMS),
            PAYMENT_SUCCESS, List.of(EMAIL, IN_APP),
            COMMENT_ADDED, List.of(PUSH, IN_APP, WEBSOCKET),
            LIKE_RECEIVED, List.of(IN_APP, WEBSOCKET),
            MENTIONED, List.of(PUSH, IN_APP, WEBSOCKET),
            PASSWORD_RESET, List.of(EMAIL, SMS),
            OTP_GENERATED, List.of(SMS, EMAIL)
    );

    private EventChannelRouting() {
    }

    public static List<NotificationChannel> channelsFor(com.uber.notification.domain.model.EventType eventType) {
        return ROUTES.getOrDefault(eventType, List.of(IN_APP));
    }
}
