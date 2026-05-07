package model.exception;

public class AuctionNotFoundException extends AuctionException {
    public AuctionNotFoundException(String auctionId) {
        super("Auction not found: " + auctionId);
    }
}
