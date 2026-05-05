package model.Entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private Instant createdAt;

    protected Entity() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    protected Entity(String id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
    protected Entity(String id){
        this.id = Objects.requireNonNull(id, "id");

    }

    public String getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
    public abstract String printInfo();
}
