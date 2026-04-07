package com.bbbc.domain.item;

import java.time.Instant;

public final class Electronics extends Item {
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
            String model
    ) {
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.ELECTRONICS);
        this.brand = brand;
        this.model = model;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    @Override
    public String printInfo() {
        return "Electronics{name='%s', brand='%s', model='%s'}".formatted(getName(), brand, model);
    }
}
