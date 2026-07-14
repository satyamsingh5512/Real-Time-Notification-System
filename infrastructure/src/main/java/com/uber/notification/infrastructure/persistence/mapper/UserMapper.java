package com.uber.notification.infrastructure.persistence.mapper;

import com.uber.notification.domain.model.User;
import com.uber.notification.infrastructure.persistence.entity.UserJpaEntity;

/** Maps between the framework-agnostic domain User and its JPA persistence representation. */
public final class UserMapper {

    private UserMapper() {
    }

    public static UserJpaEntity toEntity(User user) {
        return new UserJpaEntity(
                user.getId(), user.getEmail(), user.getPasswordHash(), user.getDisplayName(),
                user.getPhoneNumber(), user.getFcmDeviceToken(),
                user.getRoles(), user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt()
        );
    }

    public static User toDomain(UserJpaEntity entity) {
        return new User(
                entity.getId(), entity.getEmail(), entity.getPasswordHash(), entity.getDisplayName(),
                entity.getPhoneNumber(), entity.getFcmDeviceToken(),
                entity.getRoles(), entity.isEnabled(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
