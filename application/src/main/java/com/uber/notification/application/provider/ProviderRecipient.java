package com.uber.notification.application.provider;

/**
 * Resolved contact details for a user, gathered by the application layer (from the User
 * aggregate / an identity service) and handed to the provider strategy so provider code
 * never needs to know how to look up an email address, phone number, or device token.
 */
public record ProviderRecipient(
        String email,
        String phoneNumber,
        String fcmDeviceToken,
        String websocketSessionUserId
) {
}
