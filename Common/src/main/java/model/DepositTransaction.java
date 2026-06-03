package model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

// Giao dịch nạp tiền.
public class DepositTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private double amount;
    private Instant timestamp;
    private String status;

    public DepositTransaction() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.status = "PENDING";
    }

    public DepositTransaction(String username, double amount) {
        this();
        this.username = username;
        this.amount = amount;
    }

    public DepositTransaction(String id, String username, double amount, String status) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.username = username;
        this.amount = amount;
        this.timestamp = Instant.now();
        this.status = status != null ? status : "PENDING";
    }

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
    // Cập nhật rồi trả trạng thái.
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
