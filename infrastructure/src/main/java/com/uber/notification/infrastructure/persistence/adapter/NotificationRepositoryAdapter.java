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
        return findHistoryForUser(userId, includeDeleted, null, null, null, page, size);
    }

    @Override
    public List<Notification> findHistoryForUser(UUID userId, boolean includeDeleted,
                                                 com.uber.notification.domain.model.EventType eventType,
                                                 Instant since, Instant before, int page, int size) {
        var pageable = PageRequest.of(page, size);
        List<com.uber.notification.infrastructure.persistence.entity.NotificationJpaEntity> results;
        if (eventType == null && since == null && before == null) {
            results = includeDeleted
                    ? jpaRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                    : jpaRepository.findByUserIdAndDeletedOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            results = jpaRepository.findFilteredHistory(userId, includeDeleted, eventType, since, before, pageable);
        }
        return results.stream().map(NotificationMapper::toDomain).toList();
    }

    @Override
    public long countUnread(UUID userId) {
        return jpaRepository.countByUserIdAndReadAtIsNullAndDeletedFalse(userId);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public int markAllRead(UUID userId) {
        return jpaRepository.markAllRead(userId);
    }

    @Override
    public long countByStatus(NotificationStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public long countTotal() {
        return jpaRepository.count();
    }

    @Override
    public long countCreatedSince(Instant since) {
        return jpaRepository.countByCreatedAtAfter(since);
    }

    @Override
    public long countUnreadTotal() {
        return jpaRepository.countByReadAtIsNullAndDeletedFalse();
    }

    @Override
    public long countReadTotal() {
        return jpaRepository.countByReadAtIsNotNull();
    }

    @Override
    public List<Notification> findByStatus(NotificationStatus status, int limit) {
        return jpaRepository.findByStatus(status, PageRequest.of(0, limit)).stream()
                .map(NotificationMapper::toDomain)
                .toList();
    }

    @Override
    public List<UUID> findIdsCreatedBefore(Instant cutoff, int limit) {
        return jpaRepository.findIdsCreatedBefore(cutoff, PageRequest.of(0, limit));
    }

    @Override
    public void deleteByIds(List<UUID> ids) {
        jpaRepository.deleteAllById(ids);
    }
}
