package model.exception;

/**
 * Không tìm thấy tài nguyên (sản phẩm, phiên đấu giá, người dùng…).
 */
public class NotFoundException extends AuctionException {

    /** Gợi ý loại tài nguyên, ví dụ {@code "item"}, {@code "auction"} — có thể null. */
    private final String resource;


    public NotFoundException(String resource, String message) {
        super(message);
        this.resource = resource;
    }


    public String getResource() {
        return resource;
    }
}
