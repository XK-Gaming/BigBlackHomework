package domain.item;

import java.time.Instant;

import domain.entity.Entity;
import domain.factory.ItemType;

public abstract class Item extends Entity {
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestPrice;
    private Instant startTime;
    private Instant endTime;
    private String sellerId;
    private ItemType itemType;

    protected Item(
            String id,
            String name,
            String description,
            double startingPrice,
            Instant startTime,
            Instant endTime,
            String sellerId,
            ItemType itemType
    ) {
        super(id);
        setName(name);
        setDescription(description);
        setStartingPrice(startingPrice);
        setStartTime(startTime);
        setEndTime(endTime);
        setSellerId(sellerId);
        setItemType(itemType);
        this.currentHighestPrice = startingPrice;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank.");
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank.");
        }
        this.description = description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        if (startingPrice <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than 0.");
        }
        this.startingPrice = startingPrice;
        if (currentHighestPrice < startingPrice) {
            currentHighestPrice = startingPrice;
        }
    }

    public double getCurrentHighestPrice() {
        return currentHighestPrice;
    }

    public void updateCurrentHighestPrice(double currentHighestPrice) {
        if (currentHighestPrice < startingPrice) {
            throw new IllegalArgumentException("Current highest price must be at least starting price.");
        }
        this.currentHighestPrice = currentHighestPrice;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null.");
        }
        if (endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Start time must not be after end time.");
        }
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        if (endTime == null || (startTime != null && endTime.isBefore(startTime))) {
            throw new IllegalArgumentException("End time must not be before start time.");
        }
        this.endTime = endTime;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        if (sellerId == null || sellerId.isBlank()) {
            throw new IllegalArgumentException("Seller id must not be blank.");
        }
        this.sellerId = sellerId;
    }

    public ItemType getItemType() {
        return itemType;
    }

    public void setItemType(ItemType itemType) {
        if (itemType == null) {
            throw new IllegalArgumentException("Item type must not be null.");
        }
        this.itemType = itemType;
    }

    public abstract String getCategory();

    @Override
    public void printInfo() {
        System.out.println("%s{id='%s', name='%s', startingPrice=%.2f, currentHighestPrice=%.2f, sellerId='%s', itemType=%s, start=%s, end=%s}"
                .formatted(getCategory(), getId(), name, startingPrice, currentHighestPrice, sellerId, itemType, startTime, endTime));
    }
}
