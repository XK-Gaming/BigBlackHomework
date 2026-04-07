package com.bbbc.server.network;

import com.bbbc.common.json.JsonUtil;
import com.bbbc.common.message.ApiMessage;
import com.bbbc.domain.observer.AuctionObserver;
import com.bbbc.domain.observer.AuctionUpdate;
import com.bbbc.server.controller.AuctionController;
import com.bbbc.server.service.AuctionManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientHandler implements Runnable, ClientConnection, AuctionObserver {
    private final Socket socket;
    private final AuctionController controller;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Set<String> subscriptions = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;

    public ClientHandler(Socket socket, AuctionController controller) throws IOException {
        this.socket = socket;
        this.controller = controller;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public void run() {
        try {
            String line;
            while (running && (line = reader.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> values = (Map<String, Object>) JsonUtil.parse(line);
                ApiMessage request = ApiMessage.fromMap(values);
                if ("SUBSCRIBE".equals(request.getAction())) {
                    Object auctionId = request.getPayload().get("auctionId");
                    if (auctionId != null) {
                        subscriptions.add(String.valueOf(auctionId));
                    }
                }
                if ("UNSUBSCRIBE".equals(request.getAction())) {
                    Object auctionId = request.getPayload().get("auctionId");
                    if (auctionId != null) {
                        subscriptions.remove(String.valueOf(auctionId));
                    }
                }
                ApiMessage response = controller.handle(request, this);
                send(response);
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    @Override
    public synchronized void send(ApiMessage message) {
        try {
            writer.write(JsonUtil.stringify(message.toMap()));
            writer.newLine();
            writer.flush();
        } catch (IOException ignored) {
            close();
        }
    }

    @Override
    public void onAuctionUpdated(AuctionUpdate update) {
        if (!subscriptions.contains(update.auctionId())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auctionId", update.auctionId());
        payload.put("itemName", update.itemName());
        payload.put("currentPrice", update.currentPrice());
        payload.put("leaderBidderId", update.leaderBidderId());
        payload.put("state", update.state().name());
        payload.put("endsAt", update.endsAt().toString());
        payload.put("message", update.message());
        send(ApiMessage.event("AUCTION_UPDATED", payload));
    }

    public void close() {
        if (!running) {
            return;
        }
        running = false;
        AuctionManager manager = AuctionManager.getInstance();
        subscriptions.forEach(auctionId -> manager.getAuctionService().unsubscribe(auctionId, this));
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
