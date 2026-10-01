package com.uber.notification.application.usecase;

import com.uber.notification.common.util.IdGenerator;
import com.uber.notification.domain.model.DigestCadence;
import com.uber.notification.domain.model.EventType;
import com.uber.notification.domain.model.NotificationChannel;
import com.uber.notification.domain.model.NotificationIntent;
import com.uber.notification.domain.model.UserPreference;
import com.uber.notification.domain.repository.UserPreferenceRepository;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;

/** CRUD use case backing the Notification Preferences REST API. */
public class ManageUserPreferenceUseCase {

    private final UserPreferenceRepository preferenceRepository;

    public ManageUserPreferenceUseCase(UserPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public List<UserPreference> getAllForUser(UUID userId) {
        return preferenceRepository.findAllByUserId(userId);
    }

    public UserPreference setChannelOptIn(UUID userId, EventType eventType, NotificationChannel channel, boolean enabled) {
        UserPreference preference = preferenceRepository.findByUserIdAndEventType(userId, eventType)
                .orElseGet(() -> new UserPreference(IdGenerator.newId(), userId, eventType,
                        new EnumMap<>(NotificationChannel.class), false, 0, 0, Instant.now()));
        preference.setChannelEnabled(channel, enabled);
        return preferenceRepository.save(preference);
    }

    public UserPreference setQuietHours(UUID userId, EventType eventType, boolean enabled, int startHour, int endHour) {
        UserPreference preference = preferenceRepository.findByUserIdAndEventType(userId, eventType)
                .orElseGet(() -> new UserPreference(IdGenerator.newId(), userId, eventType,
                        new EnumMap<>(NotificationChannel.class), false, 0, 0, Instant.now()));
        preference.setQuietHours(enabled, startHour, endHour);
        return preferenceRepository.save(preference);
    }

    public UserPreference setIntent(UUID userId, EventType eventType, NotificationIntent intent) {
        UserPreference preference = findOrCreate(userId, eventType);
        preference.setIntent(intent);
        return preferenceRepository.save(preference);
    }

    public UserPreference setPushEnabled(UUID userId, EventType eventType, boolean enabled) {
        UserPreference preference = findOrCreate(userId, eventType);
        preference.setPushEnabled(enabled);
        return preferenceRepository.save(preference);
    }

    public UserPreference setDigestCadence(UUID userId, EventType eventType, DigestCadence cadence) {
        UserPreference preference = findOrCreate(userId, eventType);
        preference.setDigestCadence(cadence);
        return preferenceRepository.save(preference);
    }

    public UserPreference setFrequencyCaps(UUID userId, EventType eventType,
                                            int maxPushesPerDay, int minHoursBetweenPushes) {
        UserPreference preference = findOrCreate(userId, eventType);
        preference.setFrequencyCaps(maxPushesPerDay, minHoursBetweenPushes);
        return preferenceRepository.save(preference);
    }

    private UserPreference findOrCreate(UUID userId, EventType eventType) {
        return preferenceRepository.findByUserIdAndEventType(userId, eventType)
                .orElseGet(() -> new UserPreference(IdGenerator.newId(), userId, eventType,
                        new EnumMap<>(NotificationChannel.class), false, 0, 0, Instant.now()));
    }

    public void deletePreference(UUID userId, EventType eventType) {
        preferenceRepository.deleteByUserIdAndEventType(userId, eventType);
    }
}
