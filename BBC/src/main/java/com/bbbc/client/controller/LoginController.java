package com.bbbc.client.controller;

import com.bbbc.client.model.AuctionClient;
import com.bbbc.client.model.ClientSession;
import com.bbbc.common.json.JsonMap;
import com.bbbc.common.message.ApiMessage;

import java.util.Map;

public final class LoginController {
    private final AuctionClient auctionClient;

    public LoginController(AuctionClient auctionClient) {
        this.auctionClient = auctionClient;
    }

    public ClientSession login(String username, String password) {
        ApiMessage response = auctionClient.send("LOGIN", null, Map.of(
                "username", username,
                "password", password
        ));
        JsonMap session = new JsonMap(new JsonMap(response.getPayload()).getMap("session"));
        return new ClientSession(
                session.requireString("userId"),
                session.requireString("username"),
                session.requireString("role"),
                session.requireString("token")
        );
    }

    public void register(String username, String email, String password, String role) {
        auctionClient.send("REGISTER", null, Map.of(
                "username", username,
                "email", email,
                "password", password,
                "role", role
        ));
    }
}
