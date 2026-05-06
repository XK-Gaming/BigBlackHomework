package model.exception;

public class AuctionException extends RuntimeException {
    /**
     * Precondition: message mô tả lỗi nghiệp vụ đấu giá.
     * Postcondition: Tạo RuntimeException mang message được truyền vào.
     */
    public AuctionException(String message) {
        super(message);
    }
}
