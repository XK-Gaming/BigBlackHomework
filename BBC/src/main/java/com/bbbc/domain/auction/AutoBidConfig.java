package com.bbbc.domain.auction;

import com.bbbc.domain.entity.Entity;

public final class AutoBidConfig extends Entity {
    private final String auctionId;
    private final String bidderId;
    private final double maxBid;
    private final double increment;

    public AutoBidConfig(String auctionId, String bidderId, double maxBid, double increment) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.maxBid = maxBid;
        this.increment = increment;
    }

    public String getAuctionId() {
        return auctionId;
    }

    public String getBidderId() {
        return bidderId;
    }

    public double getMaxBid() {
        return maxBid;
    }

    public double getIncrement() {
        return increment;
    }
}
