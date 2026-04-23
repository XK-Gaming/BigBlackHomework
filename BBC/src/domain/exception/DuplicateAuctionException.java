package domain.exception;

public class DuplicateAuctionException extends RuntimeException {
    public DuplicateAuctionException(String auctionId) {
        super("Auction ID đã tồn tại: " + auctionId);
    }
}
