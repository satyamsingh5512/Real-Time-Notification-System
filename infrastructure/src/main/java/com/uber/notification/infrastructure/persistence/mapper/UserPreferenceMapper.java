package com.uber.notification.infrastructure.persistence.mapper;

import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.infrastructure.persistence.entity.UserPreferenceJpaEntity;

import java.util.EnumMap;
import java.util.Map;

public final class UserPreferenceMapper {

    private UserPreferenceMapper() {
    }

    public static UserPreferenceJpaEntity toEntity(UserPreference p) {
        return new UserPreferenceJpaEntity(
                p.getId(), p.getUserId(), p.getEventType(), p.getChannelOptIn(),
                p.isQuietHoursEnabled(), p.getQuietHoursStart(), p.getQuietHoursEnd(), p.getUpdatedAt()
        );
    }

    public static UserPreference toDomain(UserPreferenceJpaEntity e) {
        Map<NotificationChannel, Boolean> optIn = e.getChannelOptIn() != null
                ? new EnumMap<>(e.getChannelOptIn())
                : new EnumMap<>(NotificationChannel.class);
        return new UserPreference(
                e.getId(), e.getUserId(), e.getEventType(), optIn,
                e.isQuietHoursEnabled(), e.getQuietHoursStart(), e.getQuietHoursEnd(), e.getUpdatedAt()
        );
    }
}
