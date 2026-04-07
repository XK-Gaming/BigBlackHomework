package com.bbbc.domain.item;

import java.time.Instant;

public final class Vehicle extends Item {
    private final String manufacturer;
    private final int year;

    public Vehicle(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String manufacturer,
            int year
    ) {
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.VEHICLE);
        this.manufacturer = manufacturer;
        this.year = year;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public int getYear() {
        return year;
    }

    @Override
    public String printInfo() {
        return "Vehicle{name='%s', manufacturer='%s', year=%d}".formatted(getName(), manufacturer, year);
    }
}
