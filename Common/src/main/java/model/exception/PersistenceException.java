package model.exception;

/**
 * Lỗi truy cập / ghi cơ sở dữ liệu hoặc lưu trữ bền.
 */
public class PersistenceException extends AuctionException {

    public PersistenceException(String message) {
        super(message);
    }

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
