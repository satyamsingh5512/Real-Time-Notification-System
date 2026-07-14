package com.uber.notification.domain.repository;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationTemplate;

import java.util.List;
import java.util.Optional;

public interface NotificationTemplateRepository {

    Optional<NotificationTemplate> findActiveByCodeAndChannel(String code, NotificationChannel channel);

    Optional<NotificationTemplate> findActiveByCodeAndChannelAndLocale(String code, NotificationChannel channel, String locale);

    List<NotificationTemplate> findAllVersionsByCode(String code);

    NotificationTemplate save(NotificationTemplate template);
}
