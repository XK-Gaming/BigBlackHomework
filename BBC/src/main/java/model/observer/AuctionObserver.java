package model.observer;

import model.auction.Auction;
import model.auction.BidTransaction;

public interface AuctionObserver {
    void onNewBidPlaced(Auction auction, BidTransaction newBid);

    void onAuctionFinished(Auction auction);
}
