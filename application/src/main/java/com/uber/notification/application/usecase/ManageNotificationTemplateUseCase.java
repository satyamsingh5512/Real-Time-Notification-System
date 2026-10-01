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
        return createNewVersion(code, channel, locale, subjectTemplate, bodyTemplate, 100);
    }

    /**
     * Gradual rollout (DoorDash pattern): the new version serves {@code trafficPct}%
     * of users (stable per-user hash); the rest keep rendering with the previous
     * version. {@code trafficPct=100} preserves the old cutover behavior.
     */
    public NotificationTemplate createNewVersion(String code, NotificationChannel channel, String locale,
                                                  String subjectTemplate, String bodyTemplate, int trafficPct) {
        List<NotificationTemplate> existing = templateRepository.findAllVersionsByCode(code);
        int nextVersion = existing.stream()
                .filter(t -> t.getChannel() == channel && t.getLocale().equals(locale))
                .mapToInt(NotificationTemplate::getVersion)
                .max()
                .orElse(0) + 1;

        // The single-active invariant (partial unique index uq_templates_active_version)
        // is preserved: the newest version is always the active one, and the rollout
        // split happens at render time via traffic_pct (see TemplateRolloutSelector).
        existing.stream()
                .filter(t -> t.getChannel() == channel && t.getLocale().equals(locale) && t.isActive())
                .forEach(t -> {
                    t.deactivate();
                    templateRepository.save(t);
                });

        NotificationTemplate template = new NotificationTemplate(
                IdGenerator.newId(), code, channel, nextVersion, subjectTemplate, bodyTemplate,
                locale, true, Instant.now(), trafficPct
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
