package com.uber.notification.infrastructure.persistence.jpa;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.infrastructure.persistence.entity.UserPreferenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserPreferenceJpaRepository extends JpaRepository<UserPreferenceJpaEntity, UUID> {
    Optional<UserPreferenceJpaEntity> findByUserIdAndEventType(UUID userId, EventType eventType);
    List<UserPreferenceJpaEntity> findAllByUserId(UUID userId);
    void deleteByUserIdAndEventType(UUID userId, EventType eventType);
}
