package com.bbbc.server.controller;

import com.bbbc.common.dto.UserSessionDto;
import com.bbbc.common.json.JsonMap;
import com.bbbc.common.message.ApiMessage;
import com.bbbc.domain.auction.Auction;
import com.bbbc.domain.exception.AuctionException;
import com.bbbc.domain.observer.AuctionObserver;
import com.bbbc.domain.observer.AuctionUpdate;
import com.bbbc.domain.user.User;
import com.bbbc.domain.user.UserRole;
import com.bbbc.server.network.ClientConnection;
import com.bbbc.server.service.AuctionManager;
import com.bbbc.server.service.AuctionService;
import com.bbbc.server.service.AuthenticationService;

import java.util.List;
import java.util.Map;

public final class AuctionController {
    private final AuthenticationService authenticationService;
    private final AuctionService auctionService;

    public AuctionController(AuctionManager auctionManager) {
        this.authenticationService = auctionManager.getAuthenticationService();
        this.auctionService = auctionManager.getAuctionService();
    }

    public ApiMessage handle(ApiMessage request, ClientConnection connection) {
        try {
            JsonMap payload = new JsonMap(request.getPayload());
            return switch (request.getAction()) {
                case "REGISTER" -> handleRegister(request, payload);
                case "LOGIN" -> handleLogin(request, payload);
                case "LIST_AUCTIONS" -> ApiMessage.response(
                        request.getRequestId(),
                        request.getAction(),
                        true,
                        "Fetched auctions",
                        Map.of("auctions", auctionService.listAuctions().stream().map(summary -> summary.toMap()).toList())
                );
                case "GET_AUCTION" -> ApiMessage.response(
                        request.getRequestId(),
                        request.getAction(),
                        true,
                        "Fetched auction detail",
                        Map.of("auction", auctionService.getAuctionDetail(payload.requireString("auctionId")).toMap())
                );
                case "CREATE_AUCTION" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.createAuction(user, request.getPayload()).getId()
                ));
                case "UPDATE_AUCTION" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.updateAuction(user, payload.requireString("auctionId"), request.getPayload()).getId()
                ));
                case "DELETE_AUCTION" -> handleAuthenticated(request, user -> {
                    auctionService.deleteAuction(user, payload.requireString("auctionId"));
                    return Map.of();
                });
                case "PLACE_BID" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.placeBid(user, payload.requireString("auctionId"), payload.getDouble("amount")).toMap()
                ));
                case "REGISTER_AUTO_BID" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.registerAutoBid(
                                user,
                                payload.requireString("auctionId"),
                                payload.getDouble("maxBid"),
                                payload.getDouble("increment")
                        ).toMap()
                ));
                case "SUBSCRIBE" -> handleSubscribe(request, payload, connection);
                case "UNSUBSCRIBE" -> handleUnsubscribe(request, payload, connection);
                case "MARK_PAID" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.markPaid(user, payload.requireString("auctionId")).toMap()
                ));
                case "CANCEL_AUCTION" -> handleAuthenticated(request, user -> Map.of(
                        "auction", auctionService.cancelAuction(user, payload.requireString("auctionId")).toMap()
                ));
                default -> ApiMessage.response(request.getRequestId(), request.getAction(), false, "Unknown action", Map.of());
            };
        } catch (AuctionException | IllegalArgumentException exception) {
            return ApiMessage.response(request.getRequestId(), request.getAction(), false, exception.getMessage(), Map.of());
        }
    }

    private ApiMessage handleRegister(ApiMessage request, JsonMap payload) {
        User user = authenticationService.register(
                payload.requireString("username"),
                payload.getString("email"),
                payload.requireString("password"),
                UserRole.valueOf(payload.requireString("role").toUpperCase())
        );
        return ApiMessage.response(
                request.getRequestId(),
                request.getAction(),
                true,
                "User registered",
                Map.of("userId", user.getId())
        );
    }

    private ApiMessage handleLogin(ApiMessage request, JsonMap payload) {
        String token = authenticationService.login(payload.requireString("username"), payload.requireString("password"));
        User user = authenticationService.requireUserByToken(token);
        return ApiMessage.response(
                request.getRequestId(),
                request.getAction(),
                true,
                "Login successful",
                Map.of("session", UserSessionDto.from(user, token).toMap())
        );
    }

    private ApiMessage handleAuthenticated(ApiMessage request, PayloadHandler handler) {
        User user = authenticationService.requireUserByToken(request.getToken());
        Map<String, Object> payload = handler.handle(user);
        return ApiMessage.response(request.getRequestId(), request.getAction(), true, "Success", payload);
    }

    private ApiMessage handleSubscribe(ApiMessage request, JsonMap payload, ClientConnection connection) {
        if (connection instanceof AuctionObserver observer) {
            auctionService.subscribe(payload.requireString("auctionId"), observer);
        }
        return ApiMessage.response(request.getRequestId(), request.getAction(), true, "Subscribed", Map.of());
    }

    private ApiMessage handleUnsubscribe(ApiMessage request, JsonMap payload, ClientConnection connection) {
        if (connection instanceof AuctionObserver observer) {
            auctionService.unsubscribe(payload.requireString("auctionId"), observer);
        }
        return ApiMessage.response(request.getRequestId(), request.getAction(), true, "Unsubscribed", Map.of());
    }

    @FunctionalInterface
    private interface PayloadHandler {
        Map<String, Object> handle(User user);
    }
}
