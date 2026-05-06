package model.observer;

import model.auction.Auction;
import model.auction.BidTransaction;

/**
 * Observer interface for auction events.
 */
public interface AuctionObserver {
    /**
     * Precondition: auction là phiên vừa có bid mới, newBid là giao dịch bid vừa được tạo.
     * Postcondition: Observer xử lý sự kiện bid mới theo implementation cụ thể.
     * Method không trả về giá trị.
     */
    void onNewBidPlaced(Auction auction, BidTransaction newBid);
    /**
     * Precondition: auction là phiên vừa kết thúc.
     * Postcondition: Observer xử lý sự kiện kết thúc auction theo implementation cụ thể.
     * Method không trả về giá trị.
     */
    void onAuctionFinished(Auction auction);
}
