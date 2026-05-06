package model.auction;

import java.io.Serializable;
import java.time.Instant;

import model.Entity.Entity;
import model.User.Bidder;

public class BidTransaction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Username của người đặt giá. */
    private final String Usernamebidder;
    /** Số tiền user đặt trong lần bid này. */
    private final double amount;
    /** Thời điểm bid được ghi nhận. */
    private final Instant bidTime;

    /**
     * Precondition: id khác null, UsernameBidder là username người đặt giá, amount > 0,
     * và bidTime khác null.
     * Postcondition: Tạo một BidTransaction bất biến lưu thông tin lần đặt giá.
     * NOTE: Ném IllegalArgumentException nếu amount <= 0 hoặc bidTime null.
     */
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

    /**
     * Precondition: BidTransaction đã được khởi tạo.
     * Postcondition: Method trả về username của bidder.
     */
    public String getBidder() {
        return Usernamebidder;
    }

    /**
     * Precondition: BidTransaction đã được khởi tạo.
     * Postcondition: Method trả về số tiền đã đặt.
     */
    public double getAmount() {
        return amount;
    }

    /**
     * Precondition: BidTransaction đã được khởi tạo.
     * Postcondition: Method trả về thời điểm đặt giá.
     */
    public Instant getBidTime() {
        return bidTime;
    }

    @Override
    /**
     * Precondition: BidTransaction đã được khởi tạo.
     * Postcondition: Method trả về chuỗi mô tả id, bidder, amount và bidTime.
     */
    public String printInfo() {
        return "BidTransaction{id='%s', bidder='%s', amount=%.2f, bidTime=%s}"
                .formatted(getId(),Usernamebidder, amount, bidTime);
    }
}
