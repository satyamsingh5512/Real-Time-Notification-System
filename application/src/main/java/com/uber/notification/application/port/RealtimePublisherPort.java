package com.uber.notification.application.port;

/** Output port for Redis Pub/Sub (or equivalent) fan-out to WebSocket gateway instances. */
public interface RealtimePublisherPort {

    /** Publishes a real-time notification payload to a channel scoped to the target user. */
    void publishToUser(String userId, String jsonPayload);
}
