package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Item loại điện tử.
 */
public final class Electronics extends Item implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Thương hiệu thiết bị. */
    private final String brand;
    /** Model thiết bị. */
    private final String model;

    /**
     * Precondition: Các tham số mô tả thiết bị điện tử được truyền đầy đủ.
     * Postcondition: Tạo Electronics item với ItemType.ELECTRONICS, brand và model riêng.
     */
    public Electronics(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String brand,
            String model,
            String img
    ) {
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.ELECTRONICS,img );
        this.brand = brand;
        this.model = model;
    }

    /** Precondition: Electronics đã được khởi tạo. Postcondition: Method trả về brand. */
    public String getBrand() {
        return brand;
    }

    /** Precondition: Electronics đã được khởi tạo. Postcondition: Method trả về model. */
    public String getModel() {
        return model;
    }

    /**
     * Precondition: Electronics đã được khởi tạo.
     * Postcondition: Method trả về map thuộc tính riêng để DAO serialize vào description/properties.
     */
    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("brand", getBrand());
        map.put("model", getModel());
        return map;
    }
}
