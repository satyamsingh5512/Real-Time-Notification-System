package com.uber.notification.infrastructure.provider;

import com.uber.notification.application.port.RecipientResolverPort;
import com.uber.notification.application.provider.ProviderRecipient;
import com.uber.notification.common.exception.ResourceNotFoundException;
import com.uber.notification.domain.model.User;
import com.uber.notification.domain.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Resolves delivery-time contact details from the User aggregate. */
@Component
public class UserRecipientResolverAdapter implements RecipientResolverPort {

    private final UserRepository userRepository;

    public UserRecipientResolverAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ProviderRecipient resolve(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        return new ProviderRecipient(user.getEmail(), user.getPhoneNumber(), user.getFcmDeviceToken(), userId.toString());
    }
}
