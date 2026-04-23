package model.auction;

import java.time.Instant;

import model.Entity.Entity;
import model.User.Bidder;

public class BidTransaction extends Entity {
    private final Bidder bidder;
    private final double amount;
    private final Instant bidTime;

    public BidTransaction(String id, Bidder bidder, double amount, Instant bidTime) {
        super(id);
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder must not be null.");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than 0.");
        }
        if (bidTime == null) {
            throw new IllegalArgumentException("Bid time must not be null.");
        }
        this.bidder = bidder;
        this.amount = amount;
        this.bidTime = bidTime;
    }

    public Bidder getBidder() {
        return bidder;
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
                .formatted(getId(), bidder.getUsername(), amount, bidTime);
    }
}
