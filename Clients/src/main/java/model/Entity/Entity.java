package model.Entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class cho các model có định danh và thời điểm tạo.
 *
 * <p>NOTE: Entity implements {@link Serializable} để truyền qua ObjectStream (client ↔ server).
 */
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** ID bất biến của entity. */
    private final String id;

    /** Thời điểm tạo (có thể được set từ server/DB). */
    private Instant createdAt;

    /**
     * Precondition: Không có.
     * Postcondition: Tạo entity với id random UUID và createdAt = now.
     * Method returns: nothing (constructor).
     */
    protected Entity() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    /**
     * Precondition: {@code id} và {@code createdAt} khác null.
     * Postcondition: Tạo entity với id/createdAt được truyền vào.
     * Method returns: nothing (constructor).
     * @throws NullPointerException NOTE: Nếu id hoặc createdAt là null.
     */
    protected Entity(String id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Precondition: {@code id} khác null.
     * Postcondition: Tạo entity chỉ có id; createdAt không được set (giữ null).
     * NOTE: Constructor này dễ tạo state "thiếu" (createdAt null).
     * Method returns: nothing (constructor).
     */
    protected Entity(String id){
        this.id = Objects.requireNonNull(id, "id");

    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: id.
     */
    public String getId() {
        return id;
    }

    /**
     * Precondition: createdAt đã được set (có thể null nếu dùng constructor {@link #Entity(String)}).
     * Postcondition: Không đổi state.
     * Method returns: createdAt (có thể null).
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Precondition: Entity đã có đủ dữ liệu để in ra.
     * Postcondition: Không đổi state.
     * Method returns: Chuỗi thông tin entity.
     */
    public abstract String printInfo();
}
