package model.Items;

import java.time.Instant;

import model.factory.ItemType;

public class Electronics extends Item {
    private String brand;

    public Electronics(
            String id,
            String name,
            String description,
            double startingPrice,
            Instant startTime,
            Instant endTime,
            String sellerId,
            String brand
    ) {
        super(id, name, description, startingPrice, startTime, endTime, sellerId, ItemType.ELECTRONICS);
        setBrand(brand);
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        if (brand == null || brand.isBlank()) {
            throw new IllegalArgumentException("Brand must not be blank.");
        }
        this.brand = brand;
    }

    @Override
    public String getCategory() {
        return "Electronics";
    }
}
