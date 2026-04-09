package domain.auction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import domain.entity.Entity;
import domain.exception.AuctionException;
import domain.item.Item;
import domain.user.Bidder;
import domain.user.Seller;

public class Auction extends Entity {
    private final Item item;
    private final Seller seller;
    private final List<BidTransaction> bidHistory = new ArrayList<>();
    private AuctionStatus status;
    private Bidder leadingBidder;

    public Auction(String id, Item item, Seller seller) {
        super(id);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        if (seller == null) {
            throw new IllegalArgumentException("Seller must not be null.");
        }
        this.item = item;
        this.seller = seller;
        this.status = AuctionStatus.OPEN;
    }

    public Item getItem() {
        return item;
    }

    public Seller getSeller() {
        return seller;
    }

    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    public Bidder getLeadingBidder() {
        return leadingBidder;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public void start() {
        if (status != AuctionStatus.OPEN) {
            throw new AuctionException("Auction can only start from OPEN state.");
        }
        if (LocalDateTime.now().isBefore(item.getStartTime())) {
            throw new AuctionException("Auction cannot start before configured start time.");
        }
        status = AuctionStatus.RUNNING;
    }

    public void placeBid(Bidder bidder, double amount) {
        updateStatusByTime();
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionException("Cannot bid because auction is not running.");
        }
        if (amount <= item.getCurrentHighestPrice()) {
            throw new AuctionException("Bid amount must be greater than current highest price.");
        }

        BidTransaction transaction = new BidTransaction(
                "BID-" + (bidHistory.size() + 1),
                bidder,
                amount,
                LocalDateTime.now()
        );
        bidHistory.add(transaction);
        item.updateCurrentHighestPrice(amount);
        leadingBidder = bidder;
    }

    public void updateStatusByTime() {
        LocalDateTime now = LocalDateTime.now();
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID || status == AuctionStatus.FINISHED) {
            return;
        }
        if (now.isBefore(item.getStartTime())) {
            status = AuctionStatus.OPEN;
            return;
        }
        if (!now.isBefore(item.getEndTime())) {
            finish();
            return;
        }
        status = AuctionStatus.RUNNING;
    }

    public void finish() {
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID) {
            throw new AuctionException("Closed auction cannot be finished again.");
        }
        status = AuctionStatus.FINISHED;
    }

    public void markPaid() {
        if (status != AuctionStatus.FINISHED) {
            throw new AuctionException("Only a finished auction can be marked as paid.");
        }
        if (leadingBidder == null) {
            throw new AuctionException("Auction has no winner to mark as paid.");
        }
        status = AuctionStatus.PAID;
    }

    public void cancel() {
        if (status == AuctionStatus.PAID) {
            throw new AuctionException("Paid auction cannot be canceled.");
        }
        status = AuctionStatus.CANCELED;
    }

    public String getWinnerSummary() {
        updateStatusByTime();
        if (status == AuctionStatus.OPEN || status == AuctionStatus.RUNNING) {
            return "Auction has not finished yet.";
        }
        if (leadingBidder == null) {
            return "Auction finished with no winner.";
        }
        return "Winner: %s with bid %.2f".formatted(
                leadingBidder.getFullName(),
                item.getCurrentHighestPrice()
        );
    }

    @Override
    public void printInfo() {
        System.out.println("Auction{id='%s', item='%s', seller='%s', status=%s, highestPrice=%.2f}"
                .formatted(getId(), item.getName(), seller.getUsername(), getStatus(), item.getCurrentHighestPrice()));
    }
}
