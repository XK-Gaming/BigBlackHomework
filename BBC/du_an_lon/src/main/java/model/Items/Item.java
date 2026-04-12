package model.Items;


import model.entity.Entity;

import java.time.Instant;

public abstract class Item extends Entity {
    private final String name;
    private final String description;
    private final double startingPrice;
    private double currentHighestPrice;
    private final Instant auctionStartTime;
    private Instant auctionEndTime;
    private final String sellerId;
    private final ItemType itemType;

    protected Item(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            ItemType itemType
    ) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentHighestPrice = startingPrice;
        this.auctionStartTime = auctionStartTime;
        this.auctionEndTime = auctionEndTime;
        this.sellerId = sellerId;
        this.itemType = itemType;
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

    public void updateCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    public Instant getAuctionStartTime() {
        return auctionStartTime;
    }

    public Instant getAuctionEndTime() {
        return auctionEndTime;
    }

    public void updateAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    public String getSellerId() {
        return sellerId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public abstract String printInfo();
}
