package com.uber.notification.infrastructure.persistence.jpa;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.infrastructure.persistence.entity.NotificationTemplateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateJpaRepository extends JpaRepository<NotificationTemplateJpaEntity, UUID> {
    Optional<NotificationTemplateJpaEntity> findByCodeAndChannelAndActiveTrue(String code, NotificationChannel channel);
    Optional<NotificationTemplateJpaEntity> findByCodeAndChannelAndLocaleAndActiveTrue(String code, NotificationChannel channel, String locale);
    List<NotificationTemplateJpaEntity> findAllByCodeOrderByVersionDesc(String code);
}
