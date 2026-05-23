package model.exception;

/**
 * Xung đột dữ liệu (ví dụ tên đăng nhập đã tồn tại).
 */
public class ConflictException extends AuctionException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
