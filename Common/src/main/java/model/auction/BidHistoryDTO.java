package model.auction;

import java.io.Serializable;

public class BidHistoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long itemId;
    private String itemName;
    private String sellerId;
    private double myHighestBid;     // Giá cao nhất mà bidder này từng đặt cho item này
    private double currentHighestPrice; // Giá cao nhất hiện tại của phòng đấu giá
    private double minBid; // Số tiền tối thiểu để tăng giá (min increment)
    private String lastBidTime;      // Thời gian lượt đặt cuối cùng của bidder này
    private String status;           // "WINNING", "OUTBID", "WON", "LOST"

    // Giữ constructor cũ để tương thích, mặc định minBid = 0.0
    public BidHistoryDTO(long itemId, String itemName, String sellerId, double myHighestBid,
                         double currentHighestPrice, String lastBidTime, String status) {
        this(itemId, itemName, sellerId, myHighestBid, currentHighestPrice, 0.0, lastBidTime, status);
    }

    // Constructor mới có minBid để client có thể gợi ý giá đúng
    public BidHistoryDTO(long itemId, String itemName, String sellerId, double myHighestBid,
                         double currentHighestPrice, double minBid, String lastBidTime, String status) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.sellerId = sellerId;
        this.myHighestBid = myHighestBid;
        this.currentHighestPrice = currentHighestPrice;
        this.minBid = minBid;
        this.lastBidTime = lastBidTime;
        this.status = status;
    }

    // --- GETTERS ---
    public long getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getSellerId() { return sellerId; }
    public double getMyHighestBid() { return myHighestBid; }
    public double getCurrentHighestPrice() { return currentHighestPrice; }
    public double getMinBid() { return minBid; }
    public String getLastBidTime() { return lastBidTime; }
    public String getStatus() { return status; }

    // --- SETTERS (BẮT BUỘC THÊM MỚI ĐỂ PHỤC VỤ REALTIME) ---
    public void setItemId(long itemId) { this.itemId = itemId; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setMyHighestBid(double myHighestBid) { this.myHighestBid = myHighestBid; }
    public void setCurrentHighestPrice(double currentHighestPrice) { this.currentHighestPrice = currentHighestPrice; }
    public void setMinBid(double minBid) { this.minBid = minBid; }
    public void setLastBidTime(String lastBidTime) { this.lastBidTime = lastBidTime; }
    public void setStatus(String status) { this.status = status; }
}