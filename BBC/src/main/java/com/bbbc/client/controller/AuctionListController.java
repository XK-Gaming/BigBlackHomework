package com.bbbc.client.controller;

import com.bbbc.client.model.AuctionClient;
import com.bbbc.client.model.ClientSession;
import com.bbbc.common.json.JsonMap;
import com.bbbc.common.message.ApiMessage;

import java.util.List;
import java.util.Map;

public final class AuctionListController {
    private final AuctionClient auctionClient;
    private final ClientSession session;

    public AuctionListController(AuctionClient auctionClient, ClientSession session) {
        this.auctionClient = auctionClient;
        this.session = session;
    }

    public List<Map<String, Object>> loadAuctions() {
        ApiMessage response = auctionClient.send("LIST_AUCTIONS", session.getToken(), Map.of());
        return new JsonMap(response.getPayload()).getMapList("auctions");
    }
}
