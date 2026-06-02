package model.auction;

import java.io.Serializable;

public class BidHistoryDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private long itemId;
    private String itemName;
    private double myHighestBid;     // Giá cao nhất mà bidder này từng đặt cho item này
    private double currentHighestPrice; // Giá cao nhất hiện tại của phòng đấu giá
    private String lastBidTime;      // Thời gian lượt đặt cuối cùng của bidder này
    private String status;           // "WINNING", "OUTBID", "WON", "LOST"

    public BidHistoryDTO(long itemId, String itemName, double myHighestBid,
                         double currentHighestPrice, String lastBidTime, String status) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.myHighestBid = myHighestBid;
        this.currentHighestPrice = currentHighestPrice;
        this.lastBidTime = lastBidTime;
        this.status = status;
    }

    // --- GETTERS ---
    public long getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public double getMyHighestBid() { return myHighestBid; }
    public double getCurrentHighestPrice() { return currentHighestPrice; }
    public String getLastBidTime() { return lastBidTime; }
    public String getStatus() { return status; }

    // --- SETTERS (BẮT BUỘC THÊM MỚI ĐỂ PHỤC VỤ REALTIME) ---
    public void setItemId(long itemId) { this.itemId = itemId; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public void setMyHighestBid(double myHighestBid) { this.myHighestBid = myHighestBid; }
    public void setCurrentHighestPrice(double currentHighestPrice) { this.currentHighestPrice = currentHighestPrice; }
    public void setLastBidTime(String lastBidTime) { this.lastBidTime = lastBidTime; }
    public void setStatus(String status) { this.status = status; }
}