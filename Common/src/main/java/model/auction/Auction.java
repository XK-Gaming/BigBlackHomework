package model.auction;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import model.Entity.Entity;
import model.Items.Item;

public class Auction extends Entity implements Serializable {
    @Serial
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
    }

    public Auction() {
    }

    @Override
    public String printInfo() {
        return "";
    }

    public Item getItem() {
        return item;
    }

    public String getSellerID() {
        return sellerID;
    }

    public AuctionStatus getStatus() {
        updateStatusByTime();
        return status;
    }

    public AuctionStatus getRawStatus() {
        return this.status;
    }

    public void updateStatusByTime() {
        if (item == null) {
            return;
        }
        Instant now = Instant.now();

        // 1. Trạng thái kết thúc đặc biệt thì giữ nguyên
        if (this.status == AuctionStatus.CANCELLED || this.status == AuctionStatus.PAID) {
            return;
        }

        // 2. Kiểm tra mốc kết thúc
        if (now.isAfter(item.getAuctionEndTime()) || now.equals(item.getAuctionEndTime())) {
            if (this.status != AuctionStatus.FINISHED) {
                this.status = AuctionStatus.FINISHED;
                // KHÔNG gọi DAO ở đây nữa
            }
        }
        // 3. Kiểm tra mốc bắt đầu
        else if (now.isAfter(item.getAuctionStartTime()) || now.equals(item.getAuctionStartTime())) {
            if (this.status == AuctionStatus.OPEN) {
                this.status = AuctionStatus.RUNNING;
                // KHÔNG gọi DAO ở đây nữa
            }
        }
        // 4. Mặc định là OPEN nếu chưa tới giờ
        else {
            this.status = AuctionStatus.OPEN;
        }
    }

    public String getLeadingBidder() {
        return leadingBidder;
    }

    public List<BidTransaction> getBidHistory() {
        return Collections.unmodifiableList(bidHistory);
    }

    public void setStatus(AuctionStatus status){
        this.status = status;
    }

    public void setLeadingBidder(String leadingBidder){
        this.leadingBidder = leadingBidder;
    }

    public void setItemId(long idItem) {
        this.itemId = idItem;
    }

    public long getItemId() {
        return itemId;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public void setBidHistory(List<BidTransaction> history) {
        this.bidHistory = history;
    }

    public double getCurrentPrice() {
        if (bidHistory == null || bidHistory.isEmpty()) {
            return (item != null) ? item.getCurrentHighestPrice() : 0.0;
        }
        BidTransaction highestBid = bidHistory.getLast();
        return highestBid.getAmount();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Auction other = (Auction) obj;
        return this.itemId == other.itemId;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(itemId);
    }
}