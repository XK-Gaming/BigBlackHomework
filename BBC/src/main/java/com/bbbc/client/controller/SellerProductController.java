package com.bbbc.client.controller;

import com.bbbc.client.model.AuctionClient;
import com.bbbc.client.model.ClientSession;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SellerProductController {
    private final AuctionClient auctionClient;
    private final ClientSession session;

    public SellerProductController(AuctionClient auctionClient, ClientSession session) {
        this.auctionClient = auctionClient;
        this.session = session;
    }

    public void createAuction(
            String itemType,
            String name,
            String description,
            double startingPrice,
            String startTime,
            String endTime,
            Map<String, Object> attributes
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("itemType", itemType);
        payload.put("name", name);
        payload.put("description", description);
        payload.put("startingPrice", startingPrice);
        payload.put("startTime", startTime);
        payload.put("endTime", endTime);
        payload.put("attributes", attributes);
        auctionClient.send("CREATE_AUCTION", session.getToken(), payload);
    }

    public void deleteAuction(String auctionId) {
        auctionClient.send("DELETE_AUCTION", session.getToken(), Map.of("auctionId", auctionId));
    }
}
