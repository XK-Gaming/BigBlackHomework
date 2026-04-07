package com.bbbc.common.dto;

import com.bbbc.domain.auction.Auction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AuctionDetailDto(
        String auctionId,
        String itemName,
        String itemDescription,
        String itemType,
        double startingPrice,
        double currentPrice,
        String sellerId,
        String leaderBidderId,
        String winnerBidderId,
        String state,
        String startsAt,
        String endsAt,
        List<Map<String, Object>> bidHistory
) {
    public static AuctionDetailDto from(Auction auction) {
        return new AuctionDetailDto(
                auction.getId(),
                auction.getItem().getName(),
                auction.getItem().getDescription(),
                auction.getItem().getItemType().name(),
                auction.getItem().getStartingPrice(),
                auction.getItem().getCurrentHighestPrice(),
                auction.getItem().getSellerId(),
                auction.getLeaderBidderId(),
                auction.getWinnerBidderId(),
                auction.getState().name(),
                auction.getStartTime().toString(),
                auction.getEndTime().toString(),
                auction.getBidHistory().stream().map(BidDto::from).map(BidDto::toMap).toList()
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("auctionId", auctionId);
        map.put("itemName", itemName);
        map.put("itemDescription", itemDescription);
        map.put("itemType", itemType);
        map.put("startingPrice", startingPrice);
        map.put("currentPrice", currentPrice);
        map.put("sellerId", sellerId);
        map.put("leaderBidderId", leaderBidderId);
        map.put("winnerBidderId", winnerBidderId);
        map.put("state", state);
        map.put("startsAt", startsAt);
        map.put("endsAt", endsAt);
        map.put("bidHistory", bidHistory);
        return map;
    }
}
