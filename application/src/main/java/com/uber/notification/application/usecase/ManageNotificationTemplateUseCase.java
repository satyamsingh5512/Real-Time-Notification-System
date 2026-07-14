package com.uber.notification.application.usecase;

import com.uber.notification.common.exception.ResourceNotFoundException;
import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.domain.repository.NotificationTemplateRepository;

import java.time.Instant;
import java.util.List;

/**
 * Manages template lifecycle: creating a new version deactivates the previous active
 * version for the same (code, channel, locale) so exactly one version is ever "active"
 * and eligible for rendering at send time, while old versions remain queryable for audit.
 */
public class ManageNotificationTemplateUseCase {

    private final NotificationTemplateRepository templateRepository;

    public ManageNotificationTemplateUseCase(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public NotificationTemplate createNewVersion(String code, NotificationChannel channel, String locale,
                                                  String subjectTemplate, String bodyTemplate) {
        List<NotificationTemplate> existing = templateRepository.findAllVersionsByCode(code);
        int nextVersion = existing.stream()
                .filter(t -> t.getChannel() == channel && t.getLocale().equals(locale))
                .mapToInt(NotificationTemplate::getVersion)
                .max()
                .orElse(0) + 1;

        existing.stream()
                .filter(t -> t.getChannel() == channel && t.getLocale().equals(locale) && t.isActive())
                .forEach(t -> {
                    t.deactivate();
                    templateRepository.save(t);
                });

        NotificationTemplate template = new NotificationTemplate(
                IdGenerator.newId(), code, channel, nextVersion, subjectTemplate, bodyTemplate,
                locale, true, Instant.now()
        );
        return templateRepository.save(template);
    }

    public NotificationTemplate getActive(String code, NotificationChannel channel) {
        return templateRepository.findActiveByCodeAndChannel(code, channel)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No active template for code=" + code + " channel=" + channel));
    }

    public List<NotificationTemplate> getVersionHistory(String code) {
        return templateRepository.findAllVersionsByCode(code);
    }
}
