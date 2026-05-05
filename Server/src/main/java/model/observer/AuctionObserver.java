package model.observer;

import model.auction.Auction;
import model.auction.BidTransaction;

/**
 * Observer interface for auction events.
 */
public interface AuctionObserver {
    void onNewBidPlaced(Auction auction, BidTransaction newBid);
    void onAuctionFinished(Auction auction);
}
