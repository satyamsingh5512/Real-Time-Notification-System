package com.uber.notification.api.dto.auth;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
        String token,
        UUID userId,
        String email,
        String displayName,
        Set<String> roles
) {
}
