package model.Items;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.auction.AuctionStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Item implements Serializable {
    private static final long serialVersionUID = 1L;

    private int databaseId;
    private String name;
    private String description;
    private double startingPrice;
    private double minBid;
    private double currentHighestPrice;
    private Instant auctionStartTime;
    private Instant auctionEndTime;
    private String sellerId;
    private ItemType itemType;
    private String img;

    private Map<String, String> properties = new HashMap<>();
    private AuctionStatus auctionStatus;

    // PROPERTY JAVAFX ĐỂ BINDING LÊN TABLEVIEW
    private transient StringProperty displayStatus = new SimpleStringProperty("");

    public Item(String name, String description, double startingPrice, Instant auctionStartTime, Instant auctionEndTime, String sellerId, ItemType itemType, String img) {
        this(name, description, startingPrice, 0, auctionStartTime, auctionEndTime, sellerId, itemType, img);
    }

    public Item(String name, String description, double startingPrice, double minBid, Instant auctionStartTime, Instant auctionEndTime, String sellerId, ItemType itemType, String img) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.minBid = minBid;
        this.currentHighestPrice = startingPrice;
        this.auctionStartTime = auctionStartTime;
        this.auctionEndTime = auctionEndTime;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.img = img;
    }

    public Item() {}

    public Item(String name, String description, double startingPrice, String sellerId, String imgData, ItemType itemType) {
        this(name, description, startingPrice, 0, sellerId, imgData, itemType);
    }

    public Item(String name, String description, double startingPrice, double minBid, String sellerId, String imgData, ItemType itemType) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.minBid = minBid;
        this.sellerId = sellerId;
        this.img = imgData;
        this.itemType = itemType;
    }

    // HÀM QUAN TRỌNG: Đồng bộ trạng thái Enum sang Text Tiếng Việt cho JavaFX UI
    public void updateStatus(AuctionStatus status) {
        this.auctionStatus = status;
        String statusText = "";
        if (status != null) {
            switch (status) {
                case OPEN -> statusText = "Sắp diễn ra";
                case RUNNING -> statusText = "Đang diễn ra";
                case FINISHED -> statusText = "Đã kết thúc";
                case CANCELLED -> statusText = "Đã hủy bỏ";
                case PAID -> statusText = "Đã thanh toán";
            }
        }
        setDisplayStatus(statusText);
    }

    public StringProperty displayStatusProperty() {
        if (displayStatus == null) {
            displayStatus = new SimpleStringProperty("");
        }
        return displayStatus;
    }

    public String getDisplayStatus() {
        return displayStatusProperty().get();
    }

    public void setDisplayStatus(String status) {
        this.displayStatusProperty().set(status);
    }

    public AuctionStatus getAuctionStatus() {
        return auctionStatus;
    }

    public void setAuctionStatus(AuctionStatus auctionStatus) {
        this.auctionStatus = auctionStatus;
        // Tự động đồng bộ hóa text hiển thị kèm theo khi set bằng tay
        updateStatus(auctionStatus);
    }

    // --- Các Getter và Setter giữ nguyên ---
    public Map<String, String> getProperties() { if (this.properties == null) this.properties = new HashMap<>(); return this.properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
    public void setDatabaseId(int id) { this.databaseId = id; }
    public int getDatabaseId() { return databaseId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public double getMinBid() { return minBid; }
    public double getCurrentHighestPrice() { return currentHighestPrice; }
    public void updateCurrentHighestPrice(double currentHighestPrice) { this.currentHighestPrice = currentHighestPrice; }
    public Instant getAuctionStartTime() { return auctionStartTime; }
    public Instant getAuctionEndTime() { return auctionEndTime; }
    public void updateAuctionEndTime(Instant auctionEndTime) { this.auctionEndTime = auctionEndTime; }
    public String getSellerId() { return sellerId; }
    public String getImg(){ return img; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStartingPrice(double startingPrice) { this.startingPrice = startingPrice; }
    public void setMinBid(double minBid) { this.minBid = minBid; }
    public void setCurrentHighestPrice(double currentHighestPrice) { this.currentHighestPrice = currentHighestPrice; }
    public void setAuctionStartTime(Instant auctionStartTime) { this.auctionStartTime = auctionStartTime; }
    public void setAuctionEndTime(Instant auctionEndTime) { this.auctionEndTime = auctionEndTime; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setItemType(ItemType itemType) { this.itemType = itemType; }
    public void setImg(String img) { this.img = img; }
    public ItemType getRawItemType() { return this.itemType; }

    public String getItemType() {
        if (itemType == null) return "";
        return switch (itemType) {
            case ART -> "Mỹ thuật";
            case ELECTRONICS -> "Điện tử";
            case VEHICLE -> "Phương tiện giao thông";
            default -> itemType.toString();
        };
    }
}