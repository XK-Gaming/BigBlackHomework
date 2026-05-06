package model.Items;


import model.Entity.Entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

/**
 * Model đại diện một sản phẩm được đưa lên sàn đấu giá.
 *
 * Trách nhiệm class: giữ thông tin mô tả item, giá khởi điểm, giá hiện tại,
 * thời gian đấu giá, người bán, loại item và ảnh.
 */
public class Item  implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Khóa chính trong bảng items. */
    private int databaseId;
    /** Tên sản phẩm. */
    private String name;
    /** Mô tả sản phẩm hoặc JSON properties khi đọc từ DB. */
    private String description;
    /** Giá khởi điểm. */
    private double startingPrice;
    /** Giá cao nhất hiện tại. */
    private double currentHighestPrice;
    /** Thời điểm bắt đầu đấu giá. */
    private Instant auctionStartTime;
    /** Thời điểm kết thúc đấu giá. */
    private Instant auctionEndTime;
    /** Username/id của người bán. */
    private String sellerId;
    /** Loại item. */
    private ItemType itemType;
    /** Tên file ảnh hoặc URL ảnh. */
    private String img;

    /**
     * Precondition: Các tham số mô tả item được truyền đầy đủ.
     * Postcondition: Tạo Item và đặt currentHighestPrice bằng startingPrice.
     */
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
    /**
     * Precondition: Không có.
     * Postcondition: Tạo Item rỗng để DAO hoặc serializer populate field sau.
     */
    public Item(){};

    /**
     * Precondition: Các tham số cơ bản được truyền từ DAO mapResultSetToItem().
     * Postcondition: Constructor hiện chưa gán field nào.
     * NOTE: Đây là điểm cần sửa nếu muốn mapResultSetToItem tạo Item đầy đủ bằng constructor này.
     */
    public Item(String name, String description, double startingPrice, String sellerId, String imgData, String itemType) {
    }

    /**
     * Precondition: Subclass có thể override để trả thuộc tính riêng.
     * Postcondition: Base Item hiện trả null.
     */
    public Map<String,String> getProperties(){
        return null;}

    /**
     * Precondition: id là khóa chính trong bảng items.
     * Postcondition: Cập nhật databaseId.
     * Method không trả về giá trị.
     */
    public void setDatabaseId(int id) {
        this.databaseId = id;
    }

    /**
     * Precondition: Item đã được khởi tạo.
     * Postcondition: Method trả về databaseId.
     */
    public int getDatabaseId() {
        return databaseId;
    }

    /**
     * Precondition: Item đã được khởi tạo.
     * Postcondition: Method trả về databaseId dạng String.
     */
    public String getId_Item() {
        return String.valueOf(databaseId);
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về tên item. */
    public String getName() {
        return name;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về mô tả item. */
    public String getDescription() {
        return description;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về giá khởi điểm. */
    public double getStartingPrice() {
        return startingPrice;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về giá cao nhất hiện tại. */
    public double getCurrentHighestPrice() {
        return currentHighestPrice;
    }

    /**
     * Precondition: currentHighestPrice là giá mới hợp lệ.
     * Postcondition: Cập nhật giá cao nhất hiện tại trong object.
     * Method không trả về giá trị.
     */
    public void updateCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về thời điểm bắt đầu. */
    public Instant getAuctionStartTime() {
        return auctionStartTime;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về thời điểm kết thúc. */
    public Instant getAuctionEndTime() {
        return auctionEndTime;
    }

    /**
     * Precondition: auctionEndTime là thời điểm kết thúc mới.
     * Postcondition: Cập nhật thời điểm kết thúc đấu giá.
     * Method không trả về giá trị.
     */
    public void updateAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về sellerId. */
    public String getSellerId() {
        return sellerId;
    }

    /** Precondition: Item đã được khởi tạo. Postcondition: Method trả về ảnh/tên file ảnh. */
    public String getImg(){
        return img;
    }

    /** Precondition: name là tên item mới. Postcondition: Cập nhật name. */
    public void setName(String name) {
        this.name = name;
    }

    /** Precondition: description là mô tả mới. Postcondition: Cập nhật description. */
    public void setDescription(String description) {
        this.description = description;
    }

    /** Precondition: startingPrice là giá khởi điểm mới. Postcondition: Cập nhật startingPrice. */
    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    /** Precondition: currentHighestPrice là giá cao nhất mới. Postcondition: Cập nhật currentHighestPrice. */
    public void setCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    /** Precondition: auctionStartTime là thời điểm bắt đầu mới. Postcondition: Cập nhật auctionStartTime. */
    public void setAuctionStartTime(Instant auctionStartTime) {
        this.auctionStartTime = auctionStartTime;
    }

    /** Precondition: auctionEndTime là thời điểm kết thúc mới. Postcondition: Cập nhật auctionEndTime. */
    public void setAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    /** Precondition: sellerId là id/username người bán. Postcondition: Cập nhật sellerId. */
    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    /** Precondition: itemType là enum loại item. Postcondition: Cập nhật itemType. */
    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    /** Precondition: img là tên file hoặc URL ảnh. Postcondition: Cập nhật img. */
    public void setImg(String img) {
        this.img = img;
    }

    /**
     * Precondition: itemType đã được set.
     * Postcondition: Method trả về tên loại item dạng tiếng Việt để lưu/hiển thị.
     * NOTE: Có thể NullPointerException nếu itemType null.
     */
    public String getItemType() {
            if(itemType.equals(ItemType.ART)){
                return "Mỹ thuật";
            }
            if (itemType.equals(ItemType.ELECTRONICS)){
                return "Đện tử";
            }
            if (itemType.equals(ItemType.VEHICLE)){
                return "Phương tiện giao thông";
            }
            return "";
    }

}
