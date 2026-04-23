package model.Entity;

public abstract class Entity {
    private final String id;

    protected Entity(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Id must not be blank.");
        }
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public abstract void printInfo();
}
