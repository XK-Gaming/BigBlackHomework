package model.Items;


import model.User.UserRole;
import model.entity.Entity;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public abstract class Item extends Entity {
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestPrice;
    private Instant auctionStartTime;
    private Instant auctionEndTime;
    private String sellerId;
    private ItemType itemType;
    private byte[] img;
    public Item(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            ItemType itemType,
            byte[] img
    ) {
        this.name = name;
        this.description = description;
        this.startingPrice = startingPrice;
        this.currentHighestPrice = startingPrice;
        this.auctionStartTime = auctionStartTime;
        this.auctionEndTime = auctionEndTime;
        this.sellerId = sellerId;
        this.itemType = itemType;
        this.img = img;
    }
    public Item(){};
    public Map<String,String> getProperties(){
        return null;}


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

    public byte[] getImg(){
        return img;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStartingPrice(double startingPrice) {
        this.startingPrice = startingPrice;
    }

    public void setCurrentHighestPrice(double currentHighestPrice) {
        this.currentHighestPrice = currentHighestPrice;
    }

    public void setAuctionStartTime(Instant auctionStartTime) {
        this.auctionStartTime = auctionStartTime;
    }

    public void setAuctionEndTime(Instant auctionEndTime) {
        this.auctionEndTime = auctionEndTime;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public void setItemType(ItemType itemType) {
        this.itemType = itemType;
    }

    public void setImg(byte[] img) {
        this.img = img;
    }

    public String getItemType() {
            if(itemType.equals(ItemType.ART)){
                return "Mỹ thuật";
            }
            if (itemType.equals(ItemType.ELECTRONICS)){
                return "Đện tử";
            }
            if (itemType.equals(ItemType.VEHICLE)){
                return "Phương tiện giao thông";
            }
            return "";
    }

    public abstract String printInfo();
}
