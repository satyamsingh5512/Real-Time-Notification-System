package com.uber.notification.infrastructure.persistence.jpa;

import com.uber.notification.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    Optional<UserJpaEntity> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("select u.id from UserJpaEntity u order by u.createdAt asc")
    List<UUID> findAllIds(Pageable pageable);
}
