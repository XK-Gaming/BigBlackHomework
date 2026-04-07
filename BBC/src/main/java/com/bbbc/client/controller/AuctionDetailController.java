package com.bbbc.client.controller;

import com.bbbc.client.model.AuctionClient;
import com.bbbc.client.model.ClientSession;
import com.bbbc.common.json.JsonMap;
import com.bbbc.common.message.ApiMessage;

import java.util.Map;

public final class AuctionDetailController {
    private final AuctionClient auctionClient;
    private final ClientSession session;

    public AuctionDetailController(AuctionClient auctionClient, ClientSession session) {
        this.auctionClient = auctionClient;
        this.session = session;
    }

    public Map<String, Object> loadAuction(String auctionId) {
        ApiMessage response = auctionClient.send("GET_AUCTION", session.getToken(), Map.of("auctionId", auctionId));
        return new JsonMap(response.getPayload()).getMap("auction");
    }

    public Map<String, Object> placeBid(String auctionId, double amount) {
        ApiMessage response = auctionClient.send("PLACE_BID", session.getToken(), Map.of(
                "auctionId", auctionId,
                "amount", amount
        ));
        return new JsonMap(response.getPayload()).getMap("auction");
    }

    public Map<String, Object> registerAutoBid(String auctionId, double maxBid, double increment) {
        ApiMessage response = auctionClient.send("REGISTER_AUTO_BID", session.getToken(), Map.of(
                "auctionId", auctionId,
                "maxBid", maxBid,
                "increment", increment
        ));
        return new JsonMap(response.getPayload()).getMap("auction");
    }

    public void subscribe(String auctionId) {
        auctionClient.send("SUBSCRIBE", session.getToken(), Map.of("auctionId", auctionId));
    }

    public void unsubscribe(String auctionId) {
        auctionClient.send("UNSUBSCRIBE", session.getToken(), Map.of("auctionId", auctionId));
    }
}
