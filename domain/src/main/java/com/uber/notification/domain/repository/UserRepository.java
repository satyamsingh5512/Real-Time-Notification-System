package com.uber.notification.domain.repository;

import com.uber.notification.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/** Output port for user persistence. Implemented by a JPA adapter in the infrastructure module. */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    User save(User user);
}
