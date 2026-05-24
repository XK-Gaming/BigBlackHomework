package model.Items;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import model.auction.AuctionStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Item implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int databaseId;  // Tự động tăng khi lưu vào DB
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestPrice;
    private Instant auctionStartTime;
    private Instant auctionEndTime;
    private String sellerId;
    private ItemType itemType;
    private String img;

    // Thêm biến để lưu trữ các thuộc tính động (JSON)
    private Map<String, String> properties = new HashMap<>();

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

    public Item() {}

    public Item(String name, String description, double startingPrice, String sellerId, String imgData, ItemType itemType) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.sellerId = sellerId;
        this.img = imgData;
        this.itemType = itemType;
    }

    public Map<String, String> getProperties() {
        if (this.properties == null) {
            this.properties = new HashMap<>();
        }
        return this.properties;
    }

    // ✅ Đã thêm: Giúp ControllerEditProduct nạp thông tin động khi bấm nút Save
    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

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


    public Instant getAuctionStartTime() {
        return auctionStartTime;
    }

    public Instant getAuctionEndTime() {
        return auctionEndTime;
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

    // Lấy đối tượng Enum gốc (Rất quan trọng cho việc kiểm tra logic)
    public ItemType getRawItemType() {
        return this.itemType;
    }


    // ✅ Giữ nguyên phục vụ TableView hiển thị Tiếng Việt
    public String getItemType() {
        if (itemType == null) {
            return "";
        }
        if (itemType == ItemType.ART) {
            return "Mỹ thuật";
        }
        if (itemType == ItemType.ELECTRONICS) {
            return "Điện tử";
        }
        if (itemType == ItemType.VEHICLE) {
            return "Phương tiện giao thông";
        }
        return itemType.toString();
    }

    // 1. Khai báo Property
    private transient StringProperty displayStatus = new SimpleStringProperty("");
    public StringProperty displayStatusProperty() {
        if (displayStatus == null) {
            displayStatus = new SimpleStringProperty("");
        }
        return displayStatus;
    }

    // 4. Setter để cập nhật giá trị
    public void setDisplayStatus(String status) {
        this.displayStatusProperty().set(status);
    }


    public AuctionStatus getAuctionStatus() {
        return auctionStatus;
    }

    public void setAuctionStatus(AuctionStatus auctionStatus) {
        this.auctionStatus = auctionStatus;
    }
}
