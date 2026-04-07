package com.bbbc.domain.user;

import com.bbbc.domain.entity.Entity;

import java.time.Instant;
import java.util.UUID;

public abstract class User extends Entity {
    private final String username;
    private final String email;
    private final String passwordHash;
    private final UserRole role;

    protected User(String username, String email, String passwordHash, UserRole role) {
        this(username, email, passwordHash, role, null, null);
    }

    protected User(String username, String email, String passwordHash, UserRole role, String id, Instant createdAt) {
        super(id == null ? UUID.randomUUID().toString() : id, createdAt == null ? Instant.now() : createdAt);
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public abstract String printInfo();
}
