package com.uber.notification.infrastructure.persistence.jpa;

import com.uber.notification.domain.model.NotificationStatus;
import com.uber.notification.infrastructure.persistence.entity.NotificationJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {

    Optional<NotificationJpaEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("""
            select n from NotificationJpaEntity n
            where (n.status = 'PENDING' or n.status = 'RETRYING'
                   or (n.status = 'SCHEDULED' and n.scheduledFor <= :now))
            order by n.createdAt asc
            """)
    List<NotificationJpaEntity> findDueForDelivery(@Param("now") Instant now, Pageable pageable);

    List<NotificationJpaEntity> findByUserIdAndDeletedOrderByCreatedAtDesc(UUID userId, boolean deleted, Pageable pageable);

    List<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNullAndDeletedFalse(UUID userId);

    List<NotificationJpaEntity> findByStatus(NotificationStatus status, Pageable pageable);
}
