package model.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public abstract class Entity {
    private final String id;
    private final Instant createdAt;

    protected Entity() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    protected Entity(String id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
