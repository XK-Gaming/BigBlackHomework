package domain.observer;

import domain.auction.Auction;
import domain.auction.BidTransaction;

public interface AuctionObserver {
    void onNewBidPlaced(Auction auction, BidTransaction newBid);

    void onAuctionFinished(Auction auction);
}
