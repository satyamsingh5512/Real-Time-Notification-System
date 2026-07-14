package com.uber.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Aggregate root for an authenticated principal. Framework-agnostic: no JPA/Spring Security
 * annotations here — those live on the infrastructure-layer JPA entity that maps to this model.
 */
public class User {

    private final UUID id;
    private String email;
    private String passwordHash;
    private String displayName;
    private String phoneNumber;
    private String fcmDeviceToken;
    private Set<RoleName> roles;
    private boolean enabled;
    private final Instant createdAt;
    private Instant updatedAt;

    public User(UUID id, String email, String passwordHash, String displayName,
                Set<RoleName> roles, boolean enabled, Instant createdAt, Instant updatedAt) {
        this(id, email, passwordHash, displayName, null, null, roles, enabled, createdAt, updatedAt);
    }

    public User(UUID id, String email, String passwordHash, String displayName, String phoneNumber,
                String fcmDeviceToken, Set<RoleName> roles, boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.email = Objects.requireNonNull(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.phoneNumber = phoneNumber;
        this.fcmDeviceToken = fcmDeviceToken;
        this.roles = roles;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public boolean hasRole(RoleName role) {
        return roles.contains(role);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<RoleName> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void setRoles(Set<RoleName> roles) {
        this.roles = roles;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getFcmDeviceToken() {
        return fcmDeviceToken;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        this.updatedAt = Instant.now();
    }

    public void setFcmDeviceToken(String fcmDeviceToken) {
        this.fcmDeviceToken = fcmDeviceToken;
        this.updatedAt = Instant.now();
    }
}
