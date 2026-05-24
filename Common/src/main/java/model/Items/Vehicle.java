package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Item loại phương tiện giao thông.
 */
public final class Vehicle extends Item implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Hãng sản xuất phương tiện. */
    private final String manufacturer;
    /** Năm sản xuất hoặc đời xe. */
    private final String year;

    /**
     * Precondition: Các tham số mô tả phương tiện được truyền đầy đủ.
     * Postcondition: Tạo Vehicle item với ItemType.VEHICLE, manufacturer và year riêng.
     */
    public Vehicle(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String manufacturer,
            String year,
            String img
    ) {
        this(name, description, startingPrice, 0, auctionStartTime, auctionEndTime, sellerId, manufacturer, year, img);
    }

    public Vehicle(
            String name,
            String description,
            double startingPrice,
            double minBid,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String manufacturer,
            String year,
            String img
    ) {
        super(name, description, startingPrice, minBid, auctionStartTime, auctionEndTime, sellerId, ItemType.VEHICLE, img);
        this.manufacturer = manufacturer;
        this.year = year;
    }

    /** Precondition: Vehicle đã được khởi tạo. Postcondition: Method trả về manufacturer. */
    public String getManufacturer() {
        return manufacturer;
    }

    /** Precondition: Vehicle đã được khởi tạo. Postcondition: Method trả về year. */
    public String getYear() {
        return year;
    }



    /**
     * Precondition: Vehicle đã được khởi tạo.
     * Postcondition: Method trả về map thuộc tính riêng để DAO serialize vào description/properties.
     */
    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("manufacturer", getManufacturer());
        map.put("year", getYear());
        return map;
    }
}
