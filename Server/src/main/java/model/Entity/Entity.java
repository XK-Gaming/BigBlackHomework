package model.Entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Base class cho các domain object có id và thời điểm tạo.
 */
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Id logic của entity, thường là UUID hoặc id do caller truyền vào. */
    private final String id;
    /** Thời điểm entity được tạo. */
    private Instant createdAt;

    /**
     * Precondition: Không có.
     * Postcondition: Tạo entity với UUID ngẫu nhiên và createdAt là Instant.now().
     */
    protected Entity() {
        this(UUID.randomUUID().toString(), Instant.now());
    }

    /**
     * Precondition: id và createdAt khác null.
     * Postcondition: Tạo entity với id và createdAt được truyền vào.
     * NOTE: Ném NullPointerException nếu id hoặc createdAt null.
     */
    protected Entity(String id, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }
    /**
     * Precondition: id khác null.
     * Postcondition: Tạo entity với id được truyền vào; createdAt không được set.
     * NOTE: Constructor này được dùng bởi BidTransaction trong code hiện tại.
     */
    protected Entity(String id){
        this.id = Objects.requireNonNull(id, "id");

    }

    /**
     * Precondition: Entity đã được khởi tạo.
     * Postcondition: Method trả về id của entity.
     */
    public String getId() {
        return id;
    }

    /**
     * Precondition: Entity đã được khởi tạo.
     * Postcondition: Method trả về createdAt, có thể null nếu dùng constructor chỉ có id.
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    /**
     * Precondition: Subclass đã được khởi tạo.
     * Postcondition: Subclass trả về chuỗi mô tả thông tin chính của object.
     */
    public abstract String printInfo();
}
