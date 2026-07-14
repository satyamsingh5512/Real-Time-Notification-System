package com.uber.notification.infrastructure.persistence.adapter;

import com.uber.notification.domain.model.Notification;
import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.domain.repository.NotificationRepository;
import com.uber.notification.infrastructure.persistence.jpa.NotificationJpaRepository;
import com.uber.notification.infrastructure.persistence.mapper.NotificationMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class NotificationRepositoryAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationRepositoryAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        var saved = jpaRepository.save(NotificationMapper.toEntity(notification));
        return NotificationMapper.toDomain(saved);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return jpaRepository.findById(id).map(NotificationMapper::toDomain);
    }

    @Override
    public Optional<Notification> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(NotificationMapper::toDomain);
    }

    @Override
    public List<Notification> findDueForDelivery(Instant now, int limit) {
        return jpaRepository.findDueForDelivery(now, PageRequest.of(0, limit)).stream()
                .map(NotificationMapper::toDomain)
                .toList();
    }

    @Override
    public List<Notification> findHistoryForUser(UUID userId, boolean includeDeleted, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var results = includeDeleted
                ? jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : jpaRepository.findByUserIdAndDeletedOrderByCreatedAtDesc(userId, false, pageable);
        return results.stream().map(NotificationMapper::toDomain).toList();
    }

    @Override
    public long countUnread(UUID userId) {
        return jpaRepository.countByUserIdAndReadAtIsNullAndDeletedFalse(userId);
    }

    @Override
    public List<Notification> findByStatus(NotificationStatus status, int limit) {
        return jpaRepository.findByStatus(status, PageRequest.of(0, limit)).stream()
                .map(NotificationMapper::toDomain)
                .toList();
    }
}
