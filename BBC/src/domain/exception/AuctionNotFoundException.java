package domain.exception;

public class AuctionNotFoundException extends RuntimeException {
    public AuctionNotFoundException(String auctionId) {
        super("Không tìm thấy auction: " + auctionId);
    }
}