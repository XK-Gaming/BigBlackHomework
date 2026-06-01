package model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class DepositTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private double amount;
    private Instant timestamp; // TỐI ƯU: Đổi sang Instant để đồng bộ múi giờ quốc tế UTC với BidTransaction
    private String status; // PENDING, APPROVED, REJECTED

    /**
     * Constructor mặc định: Tự sinh ID và mốc thời gian hiện tại
     */
    public DepositTransaction() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.status = "PENDING";
    }

    /**
     * Constructor nhanh cho luồng nạp tiền cơ bản
     */
    public DepositTransaction(String username, double amount) {
        this();
        this.username = username;
        this.amount = amount;
    }

    /**
     * ✅ BỔ SUNG: Constructor đầy đủ tham số
     * Giải quyết dứt điểm lỗi biên dịch khi UserService hoặc DAO cần tái tạo đối tượng từ DB / chuỗi JSON
     */
    public DepositTransaction(String id, String username, double amount, String status) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.username = username;
        this.amount = amount;
        this.timestamp = Instant.now();
        this.status = status != null ? status : "PENDING";
    }

    // ==========================================
    // GETTERS & SETTERS
    // ==========================================

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        // Loại bỏ khoảng trắng thừa đề phòng dữ liệu đầu vào lỗi
        this.username = username != null ? username.trim() : null;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        if (status != null) {
            this.status = status.trim().toUpperCase();
        } else {
            this.status = "PENDING";
        }
    }
}