package com.bbbc.domain.observer;

import com.bbbc.domain.auction.AuctionState;

import java.time.Instant;

public record AuctionUpdate(
        String auctionId,
        String itemName,
        double currentPrice,
        String leaderBidderId,
        AuctionState state,
        Instant endsAt,
        String message
) {
}
