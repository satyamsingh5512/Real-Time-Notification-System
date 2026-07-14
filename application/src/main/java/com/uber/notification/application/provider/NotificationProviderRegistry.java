package com.uber.notification.application.provider;

import com.uber.notification.domain.model.NotificationChannel;

import java.util.Map;
import java.util.Optional;

/** Registry that resolves the right Strategy implementation for a channel at runtime. */
public class NotificationProviderRegistry {

    private final Map<NotificationChannel, NotificationProvider> providersByChannel;

    public NotificationProviderRegistry(Map<NotificationChannel, NotificationProvider> providersByChannel) {
        this.providersByChannel = providersByChannel;
    }

    public Optional<NotificationProvider> resolve(NotificationChannel channel) {
        return Optional.ofNullable(providersByChannel.get(channel));
    }
}
