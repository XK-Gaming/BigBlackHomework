package domain.auction;

import java.util.Collection;

import domain.exception.AuctionException;

/**
 * Lop facade giu lai de tuong thich voi code cu.
 * Code moi nen lam viec truc tiep voi AuctionManager.
 */
@Deprecated
public final class AuctionPlatform {
    private final AuctionManager auctionManager;

    public AuctionPlatform() {
        this(AuctionManager.getInstance());
    }

    AuctionPlatform(AuctionManager auctionManager) {
        if (auctionManager == null) {
            throw new IllegalArgumentException("AuctionManager must not be null.");
        }
        this.auctionManager = auctionManager;
    }

    public void addAuction(Auction auction) {
        auctionManager.addAuction(auction);
    }

    public Auction getAuction(String auctionId) {
        Auction auction = auctionManager.findAuctionById(auctionId);
        if (auction == null) {
            throw new AuctionException("Auction not found.");
        }
        return auction;
    }

    public void removeAuction(String auctionId) {
        auctionManager.removeAuction(auctionId);
    }

    public Collection<Auction> getAllAuctions() {
        return auctionManager.getAuctions();
    }
}
