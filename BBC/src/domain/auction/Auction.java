package domain.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import domain.entity.Entity;
import domain.exception.AuctionClosedException;
import domain.exception.AuctionException;
import domain.exception.InvalidBidException;
import domain.item.Item;
import domain.observer.AuctionObserver;
import domain.user.Bidder;
import domain.user.Seller;

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
    private final List<AuctionObserver> observers = new ArrayList<>();//list cho subcribers

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
        updateStatusByTime();
        if (status != AuctionStatus.OPEN) {
            throw new AuctionClosedException("Auction can only start from OPEN state.");
        }
        // Chi cho phep bat dau khi da toi thoi diem mo dau gia.
        if (Instant.now().isBefore(item.getStartTime())) {
            throw new AuctionException("Auction cannot start before configured start time.");
        }
        status = AuctionStatus.RUNNING;
    }
    //synchronize placebid cho nhieu nguoi dung
    public synchronized void  placeBid(Bidder bidder, double amount) {
        //kiểm tra seller tự bid.
        if (bidder.getId().equals(seller.getId())) {
            throw new InvalidBidException("Seller cannot bid on their own auction.");
        }
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder must not be null.");
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

        // Moi lan bid hop le se duoc ghi vao lich su va cap nhat gia cao nhat hien tai.
        BidTransaction transaction = new BidTransaction(
                "BID-" + (bidHistory.size() + 1),
                bidder,
                amount,
                Instant.now()
        );
        bidHistory.add(transaction);
        item.updateCurrentHighestPrice(amount);
        leadingBidder = bidder;
        //update cho observer
        notifyNewBid(transaction);
    }

    public void updateStatusByTime() {
        Instant now = Instant.now();
        // Các trạng thái đóng thì không tự động thay đổi nữa.
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID || status == AuctionStatus.FINISHED) {
            return;
        }
        // Chỉ chạy được nếu status == OPEN và đã tới giờ bắt đầu
        if (now.isBefore(item.getStartTime())) {
            status = AuctionStatus.OPEN;
            return;
        }
        // Het thoi gian thi chuyen sang FINISHED ngay ca khi khong ai goi start/finish thu cong.
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
        // // Không có người thắng → IllegalStateException (lỗi logic, không phải trạng thái phiên)
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
        // Neu khong co ai bid, phien dau gia ket thuc nhung khong co nguoi thang.
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

    //observer stuffs
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
