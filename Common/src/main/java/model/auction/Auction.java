package model.auction;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import model.Entity.Entity;
import model.Items.Item;

// Model phiên đấu giá.
public class Auction extends Entity implements Serializable {
    private static final long serialVersionUID = 1L;
    private long itemId;
    private Item item;
    private String sellerID;
    private List<BidTransaction> bidHistory = new ArrayList<>();
    private AuctionStatus status;
    private String leadingBidder;

    public Auction(String id, Item item, String sellerID, Instant createdAt) {
        super(id, createdAt);
        if (item == null) {
            throw new IllegalArgumentException("Item must not be null.");
        }
        this.item = item;
        this.sellerID = sellerID;
        this.status = AuctionStatus.OPEN;
        this.item.setAuctionStatus(AuctionStatus.OPEN);
    }

    public Auction() {}
    // Cập nhật rồi trả trạng thái.
    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    public AuctionStatus getStoredStatus() {
        return status;
    }
    // Đồng bộ trạng thái theo giờ.
    public void updateStatusByTime() {
        if (item == null) return;
        Instant now = Instant.now();

        if (this.status == AuctionStatus.CANCELLED || this.status == AuctionStatus.PAID || this.status == null) {

            if (item.getAuctionStatus() != this.status) {
                item.setAuctionStatus(this.status);
            }
            return;
        }

        AuctionStatus targetStatus;
        if (now.isAfter(item.getAuctionEndTime()) || now.equals(item.getAuctionEndTime())) {
            targetStatus = AuctionStatus.FINISHED;
        } else if (now.isAfter(item.getAuctionStartTime()) || now.equals(item.getAuctionStartTime())) {
            targetStatus = AuctionStatus.RUNNING;
        } else {
            targetStatus = AuctionStatus.OPEN;
        }

        if (this.status != targetStatus) {
            this.status = targetStatus;
        }

        if (item.getAuctionStatus() != targetStatus) {
            item.setAuctionStatus(targetStatus);
        }
    }

    public void setStatus(AuctionStatus status){
        this.status = status;
        if (this.item != null) {
            this.item.setAuctionStatus(status);
        }
    }

    public void setItem(Item item) {
        this.item = item;
        if (item != null && this.status != null) {
            item.setAuctionStatus(this.status);
        }
    }

    @Override public String printInfo() { return ""; }
    public Item getItem() { return item; }
    public String getSellerID() { return sellerID; }
    public String getDefaultBidder() { return "Người bán"; }
    public String getLeadingBidder() { return leadingBidder; }
    public void setLeadingBidder(String leadingBidder){ this.leadingBidder = leadingBidder; }
    // Lấy lịch sử bid.
    public List<BidTransaction> getBidHistory() { return Collections.unmodifiableList(bidHistory); }
    public void setItemId(long idItem) { this.itemId = idItem; }
    public long getItemId() { return itemId; }
    public void setBidHistory(List history) { this.bidHistory = history; }
    public AuctionStatus getRawStatus() { return this.status; }
    // Lấy giá hiện tại.
    public double getCurrentPrice() {
        if (bidHistory == null || bidHistory.isEmpty()) {
            return (item != null) ? item.getCurrentHighestPrice() : 0.0;
        }
        BidTransaction highestBid = bidHistory.get(bidHistory.size() - 1);
        return highestBid.getAmount();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Auction other = (Auction) obj;
        return this.itemId == other.itemId;
    }

    @Override public int hashCode() { return Long.hashCode(itemId); }
}
