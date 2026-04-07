package com.bbbc.domain.item;

import java.time.Instant;

public final class Art extends Item {
    private final String artist;

    public Art(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String artist
    ) {
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.ART);
        this.artist = artist;
    }

    public String getArtist() {
        return artist;
    }

    @Override
    public String printInfo() {
        return "Art{name='%s', artist='%s'}".formatted(getName(), artist);
    }
}
