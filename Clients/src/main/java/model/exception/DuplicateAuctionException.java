package model.exception;

public class DuplicateAuctionException extends AuctionException {
    public DuplicateAuctionException(String auctionId) {
        super("Auction ID already exists: " + auctionId);
    }
}
