package domain.auction;

import java.time.LocalDateTime;

import domain.entity.Entity;
import domain.user.Bidder;

public class BidTransaction extends Entity {
    private final Bidder bidder;
    private final double amount;
    private final LocalDateTime bidTime;

    public BidTransaction(String id, Bidder bidder, double amount, LocalDateTime bidTime) {
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

    public LocalDateTime getBidTime() {
        return bidTime;
    }

    @Override
    public void printInfo() {
        System.out.println("BidTransaction{id='%s', bidder='%s', amount=%.2f, bidTime=%s}"
                .formatted(getId(), bidder.getUsername(), amount, bidTime));
    }
}
