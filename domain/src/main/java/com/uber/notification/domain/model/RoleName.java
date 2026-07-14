package com.uber.notification.domain.model;

/** RBAC role assigned to a User. Kept minimal; extend for finer-grained permissions if needed. */
public enum RoleName {
    ADMIN,
    SERVICE,
    USER
}
