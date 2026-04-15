package domain.user;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import domain.auction.Auction;
import domain.auction.BidTransaction;
import domain.observer.AuctionObserver;

public class Bidder extends User implements AuctionObserver {
    private final Map<String, Map<String, Object>> auctionSnapshots = new LinkedHashMap<>();

    public Bidder(String id, String fullName, String username, String password) {
        super(id, fullName, username, password);
    }

    @Override
    public String getRole() {
        return "Bidder";
    }

    public void watchAuction(Auction auction) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction must not be null.");
        }
        auction.addObserver(this);
        syncAuctionSnapshot(auction, null);
    }

    public void stopWatchingAuction(Auction auction) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction must not be null.");
        }
        auction.removeObserver(this);
    }

    public Map<String, Map<String, Object>> getAuctionSnapshots() {
        return Collections.unmodifiableMap(auctionSnapshots);
    }

    public Map<String, Object> getAuctionSnapshot(String auctionId) {
        Map<String, Object> snapshot = auctionSnapshots.get(auctionId);
        if (snapshot == null) {
            return null;
        }
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    public void onNewBidPlaced(Auction auction, BidTransaction newBid) {
        syncAuctionSnapshot(auction, newBid);
    }

    @Override
    public void onAuctionFinished(Auction auction) {
        syncAuctionSnapshot(auction, null);
    }

    private void syncAuctionSnapshot(Auction auction, BidTransaction lastBid) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("auctionId", auction.getId());
        snapshot.put("itemName", auction.getItem().getName());
        snapshot.put("status", auction.getStatus().name());
        snapshot.put("seller", auction.getSeller().getUsername());
        snapshot.put("highestPrice", auction.getItem().getCurrentHighestPrice());
        snapshot.put("leadingBidder", auction.getLeadingBidder() == null ? null : auction.getLeadingBidder().getUsername());
        snapshot.put("winnerSummary", auction.getWinnerSummary());
        if (lastBid != null) {
            snapshot.put("lastBidAmount", lastBid.getAmount());
            snapshot.put("lastBidder", lastBid.getBidder().getUsername());
            snapshot.put("lastBidTime", lastBid.getBidTime());
        }
        auctionSnapshots.put(auction.getId(), snapshot);
    }
}
