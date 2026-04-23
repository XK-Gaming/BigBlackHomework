package model.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.Entity.Entity;
import model.exception.AuctionException;
import model.Items.Item;
import model.observer.AuctionObserver;
import model.User.Bidder;
import model.User.Seller;

/**
 * Dai dien cho mot phien dau gia cua duy nhat mot item.
 * Lop nay quan ly trang thai phien dau gia va lich su bid.
 */
public class Auction extends Entity {
    private long itemId;
    private  Item item;
    private  String sellerID;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private AuctionStatus status;
    private Bidder leadingBidder;
    private final List<AuctionObserver> observers = new ArrayList<>();//list cho subcribers

    public Auction(String id, Item item, String sellerID, Instant createdAt) {
        super(id, createdAt);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        this.item = item;
        this.sellerID = sellerID;
        this.status = AuctionStatus.OPEN;
    }

    public Auction() {

    }

    public Item getItem() {
        return item;
    }

    public String getSellerID() {
        return sellerID;
    }

    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }
    public String getDefaultBidder(){
        return "Người bán";
    }
    public Bidder getLeadingBidder() {
        return leadingBidder;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    /**
     * Ý nghĩa của Collections.unmodifiableList
     * Dòng code này trả về một "Read-only list" (Danh sách chỉ đọc).
     *
     * Nếu bạn cố gắng đọc dữ liệu: Mọi thứ vẫn hoạt động bình thường. Bạn có thể dùng vòng lặp để xem danh sách hoặc lấy một phần tử ra.
     *
     * Nếu bạn cố gắng thay đổi dữ liệu: Nếu một bên thứ ba gọi phương thức này và cố gắng thực hiện các thao tác như .add() (thêm), .remove() (xóa), hoặc .clear() (xóa hết), chương trình sẽ ném ra ngoại lệ UnsupportedOperationException và dừng lại ngay lập tức.
     */
    public void start() {
        if (status != AuctionStatus.OPEN) {
            throw new AuctionException("Auction can only start from OPEN state.");
        }
        // Chi cho phep bat dau khi da toi thoi diem mo dau gia.
        if (Instant.now().isBefore(item.getAuctionStartTime())) {
            throw new AuctionException("Auction cannot start before configured start time.");
        }
        status = AuctionStatus.RUNNING;
    }
    //synchronize placebid cho nhieu nguoi dung
    public synchronized void  placeBid(Bidder bidder, double amount) {
        updateStatusByTime();
        if (status != AuctionStatus.RUNNING) {
            throw new AuctionException("Cannot bid because auction is not running.");
        }
        if (amount <= item.getCurrentHighestPrice()) {
            throw new AuctionException("Bid amount must be greater than current highest price.");
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
        // Cac trang thai dong thi khong tu dong thay doi nua theo thoi gian.
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID || status == AuctionStatus.FINISHED) {
            return;
        }
        if (now.isBefore(item.getAuctionStartTime())) {
            status = AuctionStatus.OPEN;
            return;
        }
        // Het thoi gian thi chuyen sang FINISHED ngay ca khi khong ai goi start/finish thu cong.
        if (!now.isBefore(item.getAuctionEndTime())) {
            finish();
            return;
        }
        status = AuctionStatus.RUNNING;
    }

    public void finish() {
        if (status == AuctionStatus.CANCELED || status == AuctionStatus.PAID) {
            throw new AuctionException("Closed auction cannot be finished again.");
        }
        boolean wasRunning = status != AuctionStatus.FINISHED;
        status = AuctionStatus.FINISHED;
        if (wasRunning) {
            notifyAuctionFinished();
        }
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
        // Neu khong co ai bid, phien dau gia ket thuc nhung khong co nguoi thang.
        if (leadingBidder == null) {
            return "Auction finished with no winner.";
        }
        return "Winner: %s with bid %.2f".formatted(
                leadingBidder.getName(),
                item.getCurrentHighestPrice()
        );
    }

    @Override
    public String printInfo() {
        return "Auction{id='%s', item='%s', seller='%s', status=%s, highestPrice=%.2f}"
                .formatted(getId(), item.getName(), sellerID, getStatus(), item.getCurrentHighestPrice());
    }

    //observer stuffs
    public void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }
    public void removeObserver(AuctionObserver observer) {
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

    public void setItemId(long itemId) {
        this.itemId =itemId;
    }


    public void setLeadingBidder(Bidder leadingBidder) {
        this.leadingBidder = leadingBidder;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public void setbidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory;
    }
}
