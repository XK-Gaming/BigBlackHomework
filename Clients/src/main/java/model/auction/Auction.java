package model.auction;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.Entity.Entity;
import model.exception.AuctionClosedException;
import model.exception.AuctionException;
import model.exception.InvalidBidException;
import model.Items.Item;
import model.observer.AuctionObserver;
import model.User.Bidder;
import model.User.Seller;

/**
 * Đại diện cho một phiên đấu giá của duy nhất một {@link model.Items.Item}.
 *
 * <p>Trách nhiệm:
 * <ul>
 *   <li>Quản lý trạng thái phiên đấu giá ({@link AuctionStatus}).</li>
 *   <li>Lưu lịch sử bid ({@link BidTransaction}).</li>
 *   <li>Quản lý người dẫn đầu (leading bidder).</li>
 *   <li>Thông báo cho các {@link AuctionObserver} khi có bid mới hoặc khi phiên kết thúc.</li>
 * </ul>
 *
 * <p>NOTE: Ở module client hiện class này thường được nhận từ server (payload) qua {@link network.DataPacket}.
 */
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    private long itemId;
    /** Item thuộc phiên đấu giá này. */
    private Item item;

    /** Username/id của người bán. */
    private String sellerID;

    /** Lịch sử bid (có thể rỗng). */
    private List<BidTransaction> bidHistory = new ArrayList<>();

    /** Trạng thái hiện tại của phiên. */
    private AuctionStatus status;

    /** Username/id người đang dẫn đầu (có thể null nếu chưa ai bid). */
    private String leadingBidder;

    /** Danh sách observer/subscriber nhận sự kiện từ auction. */
    private final List<AuctionObserver> observers = new ArrayList<>();//list cho subcribers

    /**
     * Precondition: {@code item} khác null; {@code sellerID} hợp lệ; {@code createdAt} khác null.
     * Postcondition: Tạo auction ở trạng thái {@link AuctionStatus#OPEN}.
     * NOTE: Không tự start; status sẽ được cập nhật theo thời gian thông qua {@link #getStatus()} hoặc {@link #updateStatusByTime()}.
     * Method returns: đối tượng {@link Auction} mới.
     * @throws IllegalArgumentException NOTE: Nếu {@code item} null.
     */
    public Auction(String id, Item item, String sellerID, Instant createdAt) {
        super(id, createdAt);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        this.item = item;
        this.sellerID = sellerID;
        this.status = AuctionStatus.OPEN;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Tạo auction rỗng (phục vụ serialize/deserialize).
     * NOTE: Sau khi tạo bằng constructor này, các field có thể null cho tới khi được set từ DB/server.
     */
    public Auction() {

    }

    /**
     * Precondition: {@link Auction} đã có item (có thể null nếu dùng constructor rỗng).
     * Postcondition: Không đổi state.
     * Method returns: {@link Item}.
     */
    public Item getItem() {
        return item;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: sellerID.
     */
    public String getSellerID() {
        return sellerID;
    }

    /**
     * Precondition: {@link Auction} có item với start/end time hợp lệ.
     * Postcondition: Gọi {@link #updateStatusByTime()} để đồng bộ status theo thời gian hiện tại.
     * Method returns: trạng thái hiện tại sau cập nhật.
     */
    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * NOTE: Method này có vẻ dùng cho UI để hiển thị mặc định khi chưa có bidder.
     * Method returns: chuỗi "Người bán".
     */
    public String getDefaultBidder(){
        return "Người bán";
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: leadingBidder (có thể null).
     */
    public String getLeadingBidder() {
        return leadingBidder;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * NOTE: Trả về danh sách chỉ-đọc để tránh bên ngoài sửa trực tiếp lịch sử bid.
     * Method returns: unmodifiable view của bidHistory.
     * @throws UnsupportedOperationException NOTE: Nếu caller cố gắng modify list trả về.
     */
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
            throw new AuctionClosedException("Auction can only start from OPEN state.");
        }
        // Chi cho phep bat dau khi da toi thoi diem mo dau gia.
        if (Instant.now().isBefore(item.getAuctionStartTime())) {
            throw new AuctionException("Auction cannot start before configured start time.");
        }
        status = AuctionStatus.RUNNING;
    }
    //synchronize placebid cho nhieu nguoi dung

    /**
     * Precondition: {@code bidder} khác null; {@code amount} là số dương; auction đang RUNNING.
     * Postcondition: (Dự kiến) thêm một {@link BidTransaction} vào history, cập nhật leadingBidder và giá hiện tại của item.
     * NOTE: Hiện method chưa hoàn thiện (chỉ validate rồi return).
     * Method returns: nothing.
     * @throws AuctionException NOTE: Nếu auction không RUNNING hoặc amount không hợp lệ.
     */
    public synchronized void  placeBid(Bidder bidder, double amount) {
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder must not be null.");
        }
        if (bidder.getUsername() != null && bidder.getUsername().equals(sellerID)) {
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


    }

    /**
     * Precondition: {@code item} có start/end time hợp lệ.
     * Postcondition: Cập nhật {@code status} theo thời gian hiện tại, trừ khi đang ở trạng thái đóng (CANCELED/PAID/FINISHED).
     * NOTE: Khi quá endTime sẽ gọi {@link #finish()} để kích hoạt notify.
     * Method returns: nothing.
     */
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

    /**
     * Precondition: Auction không ở trạng thái PAID/CANCELED.
     * Postcondition: {@code status} được set FINISHED; nếu trước đó chưa FINISHED thì notify observer.
     * Method returns: nothing.
     * @throws AuctionException NOTE: Nếu auction đã đóng (PAID/CANCELED) mà vẫn gọi finish.
     */
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

    /**
     * Precondition: Auction đang FINISHED và có {@code leadingBidder} (tức có người thắng).
     * Postcondition: {@code status} được set PAID.
     * Method returns: nothing.
     * @throws AuctionException NOTE: Nếu auction chưa FINISHED hoặc không có winner.
     */
    public void markPaid() {
        if (status != AuctionStatus.FINISHED) {
            throw new AuctionClosedException("Only a finished auction can be marked as paid.");
        }
        if (leadingBidder == null) {
            throw new AuctionException("Auction has no winner to mark as paid.");
        }
        status = AuctionStatus.PAID;
    }

    /**
     * Precondition: Auction chưa PAID.
     * Postcondition: {@code status} được set CANCELED.
     * Method returns: nothing.
     * @throws AuctionException NOTE: Nếu auction đã PAID.
     */
    public void cancel() {
        if (status == AuctionStatus.PAID) {
            throw new AuctionClosedException("Paid auction cannot be canceled.");
        }
        status = AuctionStatus.CANCELED;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Trạng thái được cập nhật theo thời gian trước khi kết luận.
     * Method returns: Chuỗi mô tả winner; nếu chưa kết thúc hoặc không có winner sẽ trả chuỗi tương ứng.
     */
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
                leadingBidder,
                item.getCurrentHighestPrice()
        );
    }

    @Override
    public String printInfo() {
        return "Auction{id='%s', item='%s', seller='%s', status=%s, highestPrice=%.2f}"
                .formatted(getId(), item.getName(), sellerID, getStatus(), item.getCurrentHighestPrice());
    }

    //observer stuffs

    /**
     * Precondition: {@code observer} khác null.
     * Postcondition: Observer được thêm vào list nếu chưa tồn tại.
     * Method returns: nothing.
     */
    public void addObserver(AuctionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    /**
     * Precondition: {@code observer} khác null.
     * Postcondition: Observer bị xoá khỏi list (nếu có).
     * Method returns: nothing.
     */
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

    /**
     * Precondition: Không có.
     * Postcondition: Gán {@code itemId}.
     * Method returns: nothing.
     */
    public void setItemId(long itemId) {
        this.itemId =itemId;
    }


    /**
     * Precondition: Có thể null (nghĩa là chưa có winner).
     * Postcondition: Cập nhật {@code leadingBidder}.
     * Method returns: nothing.
     */
    public void setLeadingBidder(String leadingBidder) {
        this.leadingBidder = leadingBidder;
    }

    /**
     * Precondition: {@code status} khác null.
     * Postcondition: Cập nhật {@code status}.
     * Method returns: nothing.
     */
    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    /**
     * Precondition: {@code bidHistory} khác null.
     * Postcondition: Thay thế toàn bộ lịch sử bid.
     * NOTE: Tên method hiện tại là {@code setbidHistory} (không theo camelCase chuẩn).
     * Method returns: nothing.
     */
    public void setbidHistory(List<BidTransaction> bidHistory) {
        this.bidHistory = bidHistory;
    }
}
