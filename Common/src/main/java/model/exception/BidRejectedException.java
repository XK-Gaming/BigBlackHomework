package model.exception;

/**
 * Đặt giá bị từ chối (nghiệp vụ hoặc lỗi lưu trữ).
 */
public class BidRejectedException extends AuctionException {

    public enum Reason {
        PRICE_TOO_LOW,
        NOT_RUNNING,
        NOT_FOUND,
        SELLER_BID,
        PERSIST,
        INVALID_INPUT
    }

    private final Reason reason;

    public BidRejectedException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BidRejectedException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
