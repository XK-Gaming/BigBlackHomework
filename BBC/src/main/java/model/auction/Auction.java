package model.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.Entity.Entity;
import model.Items.Item;
import model.User.Bidder;
import model.User.Seller;
import model.exception.AuctionClosedException;
import model.exception.AuctionException;
import model.exception.InvalidBidException;
import model.observer.AuctionObserver;

/**
 * Dai dien cho mot phien dau gia cua duy nhat mot item.
 * Lop nay quan ly trang thai phien dau gia va lich su bid.
 */
public class Auction extends Entity {
    private final Item item;
    private final Seller seller;
    private final List<BidTransaction> bidHistory = new ArrayList<>();
    private AuctionStatus status;
    private Bidder leadingBidder;
    private final List<AuctionObserver> observers = new ArrayList<>();

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
            throw new AuctionClosedException("Auction can only start from OPEN state.");
        }

        Instant now = Instant.now();
        if (now.isBefore(item.getStartTime())) {
            throw new AuctionException("Auction cannot start before configured start time.");
        }
        if (!now.isBefore(item.getEndTime())) {
            finish();
            throw new AuctionClosedException("Auction cannot start after configured end time.");
        }
        status = AuctionStatus.RUNNING;
    }

    public synchronized void placeBid(Bidder bidder, double amount) {
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder must not be null.");
        }
        if (bidder.getId().equals(seller.getId())) {
            throw new InvalidBidException("Seller cannot bid on their own auction.");
        }

        updateStatusByTime();
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionClosedException("Cannot bid because auction is not running.");
        }
        if (amount <= 0) {
            throw new InvalidBidException("Bid amount must be positive.");
        }
        if (amount <= item.getCurrentHighestPrice()) {
            throw new InvalidBidException("Bid amount must be greater than current highest price.");
        }

        BidTransaction transaction = new BidTransaction(
                "BID-" + (bidHistory.size() + 1),
                bidder,
                amount,
                Instant.now()
        );
        bidHistory.add(transaction);
        item.updateCurrentHighestPrice(amount);
        leadingBidder = bidder;
        notifyNewBid(transaction);
    }

    public void updateStatusByTime() {
        Instant now = Instant.now();
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
            throw new AuctionClosedException("Closed auction cannot be finished again.");
        }
        boolean wasRunning = status != AuctionStatus.FINISHED;
        status = AuctionStatus.FINISHED;
        if (wasRunning) {
            notifyAuctionFinished();
        }
    }

    public void markPaid() {
        if (status != AuctionStatus.FINISHED) {
            throw new AuctionClosedException("Only a finished auction can be marked as paid.");
        }
        if (leadingBidder == null) {
            throw new IllegalStateException("Auction has no winner to mark as paid.");
        }
        status = AuctionStatus.PAID;
    }

    public void cancel() {
        if (status == AuctionStatus.PAID) {
            throw new AuctionClosedException("Paid auction cannot be canceled.");
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

    public void addObserver(AuctionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer must not be null.");
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(AuctionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("Observer must not be null.");
        }
        observers.remove(observer);
    }

    private void notifyNewBid(BidTransaction transaction) {
        for (AuctionObserver observer : observers) {
            observer.onNewBidPlaced(this, transaction);
        }
    }

    private void notifyAuctionFinished() {
        for (AuctionObserver observer : observers) {
            observer.onAuctionFinished(this);
        }
    }
}
