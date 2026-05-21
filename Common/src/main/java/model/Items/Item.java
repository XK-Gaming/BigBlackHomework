package model.Items;


import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.Entity.Entity;
import model.auction.AuctionStatus;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public class Item  implements Serializable {
    private static final long serialVersionUID = 1L;
    private int databaseId;  // Khi vào trong database thì sẽ tự cấp cho 1 id
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestPrice;
    private Instant auctionStartTime;
    private Instant auctionEndTime;
    private String sellerId;
    private ItemType itemType;
    private String img;
    private AuctionStatus auctionStatus;
    public Item(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            ItemType itemType,
            String img
    ) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentHighestPrice = startingPrice;
        this.auctionStartTime = auctionStartTime;
        this.auctionEndTime = auctionEndTime;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.img = img;
    }
    public Item(){};

    public Item(String name, String description, double startingPrice, String sellerId, ItemType itemType, String imgData) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentHighestPrice = startingPrice;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.img = imgData;
    }


    public Map<String,String> getProperties(){
        return null;}

    public void setDatabaseId(int id) {
        this.databaseId = id;
    }

    public int getDatabaseId() {
        return databaseId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getCurrentHighestPrice() {
        return currentHighestPrice;
    }

    public void updateCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    public Instant getAuctionStartTime() {
        return auctionStartTime;
    }

    public Instant getAuctionEndTime() {
        return auctionEndTime;
    }

    public void updateAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getImg(){
        return img;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public void setCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    public void setAuctionStartTime(Instant auctionStartTime) {
        this.auctionStartTime = auctionStartTime;
    }

    public void setAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public void setImg(String img) {
        this.img = img;
    }

    public String getItemType() {
            if(itemType.equals(ItemType.ART)){
                return "Mỹ thuật";
            }
            if (itemType.equals(ItemType.ELECTRONICS)){
                return "Điện tử";
            }
            if (itemType.equals(ItemType.VEHICLE)){
                return "Phương tiện giao thông";
            }
            return "";
    }

    // 1. Khai báo Property
    private transient StringProperty displayStatus = new SimpleStringProperty("");
    public StringProperty displayStatusProperty() {
        if (displayStatus == null) {
            displayStatus = new SimpleStringProperty("");
        }
        return displayStatus;
    }
    // 2. Getter cho TableView (Dùng cho PropertyValueFactory)
    public String getDisplayStatus() {
        return displayStatusProperty().get();
    }

    // 4. Setter để cập nhật giá trị
    public void setDisplayStatus(String status) {
        displayStatusProperty().set(status);
    }


    public AuctionStatus getAuctionStatus() {
        return auctionStatus;
    }

    public void setAuctionStatus(AuctionStatus auctionStatus) {
        this.auctionStatus = auctionStatus;
    }
}
