package model.Items;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.auction.AuctionStatus;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Model sản phẩm.
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

    private transient StringProperty displayStatus;

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
        initDisplayStatus();
    }

    public Item() {
        initDisplayStatus();
    }

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
        initDisplayStatus();
    }
    // Cấu hình ban đầu.
    private void initDisplayStatus() {
        if (this.displayStatus == null) {
            this.displayStatus = new SimpleStringProperty("");
        }
    }
    // Cập nhật dữ liệu.
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

        final String finalStatusText = statusText;

        try {

            if (Platform.isFxApplicationThread()) {
                setDisplayStatus(finalStatusText);
            } else {

                Platform.runLater(() -> setDisplayStatus(finalStatusText));
            }
        } catch (IllegalStateException e) {

            if (e.getMessage() != null && e.getMessage().contains("Toolkit not initialized")) {
                if (this.displayStatus != null) {
                    this.displayStatus.set(finalStatusText);
                }
            } else {
                throw e;
            }
        }
    }

    public StringProperty displayStatusProperty() {
        initDisplayStatus();
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

        updateStatus(auctionStatus);
    }
    // Đọc dữ liệu.
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        initDisplayStatus();
        if (this.auctionStatus != null) {
            updateStatus(this.auctionStatus);
        }
    }

    public Map<String, String> getProperties() {
        if (this.properties == null) this.properties = new HashMap<>();
        return this.properties;
    }

    public void setProperties(Map<String, String> properties) { this.properties = properties; }
    public void setDatabaseId(int id) { this.databaseId = id; }
    public int getDatabaseId() { return databaseId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getStartingPrice() { return startingPrice; }
    public double getMinBid() { return minBid; }
    public double getCurrentHighestPrice() { return currentHighestPrice; }
    // Cập nhật dữ liệu.
    public void updateCurrentHighestPrice(double currentHighestPrice) { this.currentHighestPrice = currentHighestPrice; }
    public Instant getAuctionStartTime() { return auctionStartTime; }
    public Instant getAuctionEndTime() { return auctionEndTime; }
    // Cập nhật dữ liệu.
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
