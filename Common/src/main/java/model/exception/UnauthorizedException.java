package model.exception;

/**
 * Đăng nhập / xác thực thất bại hoặc thiếu quyền xác thực.
 */
public class UnauthorizedException extends AuctionException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
