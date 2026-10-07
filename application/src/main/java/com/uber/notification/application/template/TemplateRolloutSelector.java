package com.uber.notification.application.template;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationTemplate;
import com.uber.notification.domain.repository.NotificationTemplateRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gradual template rollout (DoorDash pattern): the newest version serves only its
 * {@code traffic_pct} bucket of users (stable hash, no flip-flopping); everyone
 * else renders with the previous version. With {@code traffic_pct=100} (default)
 * behavior is identical to the old single-active lookup.
 */
public class TemplateRolloutSelector {

    private final NotificationTemplateRepository templateRepository;

    public TemplateRolloutSelector(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    public Optional<NotificationTemplate> select(String code, NotificationChannel channel,
                                                 String locale, UUID userId) {
        List<NotificationTemplate> versions = templateRepository.findAllVersionsByCode(code).stream()
                .filter(t -> t.getChannel() == channel && t.getLocale().equals(locale))
                .sorted(Comparator.comparingInt(NotificationTemplate::getVersion).reversed())
                .toList();
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        NotificationTemplate newest = versions.get(0);
        if (newest.getTrafficPct() >= 100) {
            return newest.isActive() ? Optional.of(newest) : previousOf(versions);
        }
        int bucket = NotificationTemplate.bucketFor(userId, code);
        if (newest.servesBucket(bucket)) {
            return Optional.of(newest);
        }
        return previousOf(versions);
    }

    private Optional<NotificationTemplate> previousOf(List<NotificationTemplate> versionsDesc) {
        return versionsDesc.stream().skip(1).findFirst();
    }
}
