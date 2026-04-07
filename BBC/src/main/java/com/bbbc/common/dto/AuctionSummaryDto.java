package com.bbbc.common.dto;

import com.bbbc.domain.auction.Auction;

import java.util.LinkedHashMap;
import java.util.Map;

public record AuctionSummaryDto(
        String auctionId,
        String itemName,
        String itemType,
        double currentPrice,
        String leaderBidderId,
        String state,
        String endsAt
) {
    public static AuctionSummaryDto from(Auction auction) {
        return new AuctionSummaryDto(
                auction.getId(),
                auction.getItem().getName(),
                auction.getItem().getItemType().name(),
                auction.getItem().getCurrentHighestPrice(),
                auction.getLeaderBidderId(),
                auction.getState().name(),
                auction.getEndTime().toString()
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("auctionId", auctionId);
        map.put("itemName", itemName);
        map.put("itemType", itemType);
        map.put("currentPrice", currentPrice);
        map.put("leaderBidderId", leaderBidderId);
        map.put("state", state);
        map.put("endsAt", endsAt);
        return map;
    }
}
