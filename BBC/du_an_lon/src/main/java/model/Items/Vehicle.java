package model.Items;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Vehicle extends Item {
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
            byte[] img
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


    @Override
    public String printInfo() {
        return "Vehicle{name='%s', manufacturer='%s', year=%d}".formatted(getName(), manufacturer, year);
    }

    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("manufacturer", getManufacturer());
        map.put("year", getYear());
        return map;
    }
}
