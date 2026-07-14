package com.uber.notification.infrastructure.persistence.adapter;

import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.UserPreferenceRepository;
import com.uber.notification.infrastructure.persistence.jpa.UserPreferenceJpaRepository;
import com.uber.notification.infrastructure.persistence.mapper.UserPreferenceMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class UserPreferenceRepositoryAdapter implements UserPreferenceRepository {

    private final UserPreferenceJpaRepository jpaRepository;

    public UserPreferenceRepositoryAdapter(UserPreferenceJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<UserPreference> findByUserIdAndEventType(UUID userId, EventType eventType) {
        return jpaRepository.findByUserIdAndEventType(userId, eventType).map(UserPreferenceMapper::toDomain);
    }

    @Override
    public List<UserPreference> findAllByUserId(UUID userId) {
        return jpaRepository.findAllByUserId(userId).stream().map(UserPreferenceMapper::toDomain).toList();
    }

    @Override
    public UserPreference save(UserPreference preference) {
        var saved = jpaRepository.save(UserPreferenceMapper.toEntity(preference));
        return UserPreferenceMapper.toDomain(saved);
    }

    @Override
    public void deleteByUserIdAndEventType(UUID userId, EventType eventType) {
        jpaRepository.deleteByUserIdAndEventType(userId, eventType);
    }
}
