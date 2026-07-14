package com.uber.notification.application.usecase;

import com.uber.notification.common.exception.AuthenticationFailedException;
import com.uber.notification.common.exception.ValidationException;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.application.port.PasswordHasher;
import com.uber.notification.domain.model.RoleName;
import com.uber.notification.domain.model.User;
import com.uber.notification.domain.repository.UserRepository;

import java.time.Instant;
import java.util.Set;

/**
 * Registration + credential verification use cases. Token issuance itself (JWT signing) is
 * deliberately kept out of the application layer, since JWT is an infrastructure concern;
 * the API layer's AuthController calls {@link #authenticate} then asks a JwtService
 * (infrastructure) to mint the token for the returned User.
 */
public class AuthenticateUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public AuthenticateUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    public User register(String email, String rawPassword, String displayName) {
        if (userRepository.existsByEmail(email)) {
            throw new ValidationException("Email already registered: " + email);
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters");
        }
        User user = new User(
                IdGenerator.newId(),
                email,
                passwordHasher.hash(rawPassword),
                displayName,
                Set.of(RoleName.USER),
                true,
                Instant.now(),
                Instant.now()
        );
        return userRepository.save(user);
    }

    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid credentials"));
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException("Account disabled");
        }
        if (!passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Invalid credentials");
        }
        return user;
    }
}
