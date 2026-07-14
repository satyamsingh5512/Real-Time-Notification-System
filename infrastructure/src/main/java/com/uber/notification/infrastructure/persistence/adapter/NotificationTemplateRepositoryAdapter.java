package com.uber.notification.infrastructure.persistence.adapter;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.domain.repository.NotificationTemplateRepository;
import com.uber.notification.infrastructure.persistence.jpa.NotificationTemplateJpaRepository;
import com.uber.notification.infrastructure.persistence.mapper.NotificationTemplateMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class NotificationTemplateRepositoryAdapter implements NotificationTemplateRepository {

    private final NotificationTemplateJpaRepository jpaRepository;

    public NotificationTemplateRepositoryAdapter(NotificationTemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<NotificationTemplate> findActiveByCodeAndChannel(String code, NotificationChannel channel) {
        return jpaRepository.findByCodeAndChannelAndActiveTrue(code, channel).map(NotificationTemplateMapper::toDomain);
    }

    @Override
    public Optional<NotificationTemplate> findActiveByCodeAndChannelAndLocale(String code, NotificationChannel channel, String locale) {
        return jpaRepository.findByCodeAndChannelAndLocaleAndActiveTrue(code, channel, locale)
                .map(NotificationTemplateMapper::toDomain);
    }

    @Override
    public List<NotificationTemplate> findAllVersionsByCode(String code) {
        return jpaRepository.findAllByCodeOrderByVersionDesc(code).stream()
                .map(NotificationTemplateMapper::toDomain)
                .toList();
    }

    @Override
    public NotificationTemplate save(NotificationTemplate template) {
        var saved = jpaRepository.save(NotificationTemplateMapper.toEntity(template));
        return NotificationTemplateMapper.toDomain(saved);
    }
}
