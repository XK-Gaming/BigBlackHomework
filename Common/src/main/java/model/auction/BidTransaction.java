package model.auction;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import model.Entity.Entity;

public class BidTransaction extends Entity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String Usernamebidder;
    private final double amount;
    private final Instant bidTime;

    public BidTransaction(String id, String UsernameBidder, double amount, Instant bidTime) {
        super(id);
        if (amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than 0.");
        }
        if (bidTime == null) {
            throw new IllegalArgumentException("Bid time must not be null.");
        }
        this.Usernamebidder = UsernameBidder;
        this.amount = amount;
        this.bidTime = bidTime;
    }

    public String getBidder() {
        return Usernamebidder;
    }

    public double getAmount() {
        return amount;
    }

    public Instant getBidTime() {
        return bidTime;
    }

    @Override
    public String printInfo() {
        return "BidTransaction{id='%s', bidder='%s', amount=%.2f, bidTime=%s}"
                .formatted(getId(),Usernamebidder, amount, bidTime);
    }
}
