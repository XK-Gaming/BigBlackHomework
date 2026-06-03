package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Electronics extends Item implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String brand;

    private final String model;

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
        this(name, description, startingPrice, 0, auctionStartTime, auctionEndTime, sellerId, brand, model, img);
    }

    public Electronics(
            String name,
            String description,
            double startingPrice,
            double minBid,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String brand,
            String model,
            String img
    ) {
        super(name, description, startingPrice, minBid, auctionStartTime, auctionEndTime, sellerId, ItemType.ELECTRONICS,img );
        this.brand = brand;
        this.model = model;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("brand", getBrand());
        map.put("model", getModel());
        return map;
    }
}
