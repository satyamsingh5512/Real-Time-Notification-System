package com.uber.notification.application.port;

import com.uber.notification.application.provider.ProviderRecipient;

import java.util.UUID;

/** Output port to resolve delivery-time contact info (email/phone/device token) for a user. */
public interface RecipientResolverPort {

    ProviderRecipient resolve(UUID userId);
}
