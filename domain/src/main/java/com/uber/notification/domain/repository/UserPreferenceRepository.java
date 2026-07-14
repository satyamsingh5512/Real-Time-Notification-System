package com.uber.notification.domain.repository;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.UserPreference;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceRepository {

    Optional<UserPreference> findByUserIdAndEventType(UUID userId, EventType eventType);

    List<UserPreference> findAllByUserId(UUID userId);

    UserPreference save(UserPreference preference);

    void deleteByUserIdAndEventType(UUID userId, EventType eventType);
}
