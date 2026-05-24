package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

public class DepositTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String username;
    private double amount;
    private LocalDateTime timestamp;
    private String status; // PENDING, APPROVED, REJECTED

    public DepositTransaction() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
        this.status = "PENDING";
    }

    public DepositTransaction(String username, double amount) {
        this();
        this.username = username;
        this.amount = amount;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
