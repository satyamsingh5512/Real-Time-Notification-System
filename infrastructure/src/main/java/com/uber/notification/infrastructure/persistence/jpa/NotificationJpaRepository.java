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

    long countByStatus(NotificationStatus status);

    long countByCreatedAtAfter(Instant since);

    long countByReadAtIsNullAndDeletedFalse();

    long countByReadAtIsNotNull();

    @Query("""
            select n from NotificationJpaEntity n
            where n.userId = :userId
              and (:includeDeleted = true or n.deleted = false)
              and (:eventType is null or n.eventType = :eventType)
              and (:since is null or n.createdAt >= :since)
              and (:before is null or n.createdAt < :before)
            order by n.createdAt desc
            """)
    List<NotificationJpaEntity> findFilteredHistory(@Param("userId") UUID userId,
                                                    @Param("includeDeleted") boolean includeDeleted,
                                                    @Param("eventType") com.uber.notification.domain.model.EventType eventType,
                                                    @Param("since") Instant since,
                                                    @Param("before") Instant before,
                                                    Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update NotificationJpaEntity n set n.readAt = CURRENT_TIMESTAMP, n.updatedAt = CURRENT_TIMESTAMP
            where n.userId = :userId and n.readAt is null and n.deleted = false
            """)
    int markAllRead(@Param("userId") UUID userId);

    @Query("select n.id from NotificationJpaEntity n where n.createdAt <= :cutoff order by n.createdAt asc")
    List<UUID> findIdsCreatedBefore(@Param("cutoff") Instant cutoff, Pageable pageable);
}
