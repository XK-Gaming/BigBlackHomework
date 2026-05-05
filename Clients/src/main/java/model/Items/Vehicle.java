package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Vehicle extends Item implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String manufacturer;
    private final String year;

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
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.VEHICLE, img);
        this.manufacturer = manufacturer;
        this.year = year;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getYear() {
        return year;
    }


    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("manufacturer", getManufacturer());
        map.put("year", getYear());
        return map;
    }
}
