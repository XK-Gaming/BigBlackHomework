package com.bbbc.client.model;

import com.bbbc.common.json.JsonUtil;
import com.bbbc.common.message.ApiMessage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class AuctionClient implements AutoCloseable {
    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Map<String, CompletableFuture<ApiMessage>> pendingRequests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Consumer<ApiMessage>> eventListeners = new CopyOnWriteArrayList<>();

    public AuctionClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        Thread listenerThread = new Thread(this::listen, "auction-client-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public ApiMessage send(String action, String token, Map<String, Object> payload) {
        ApiMessage request = ApiMessage.request(action, token, payload);
        CompletableFuture<ApiMessage> future = new CompletableFuture<>();
        pendingRequests.put(request.getRequestId(), future);
        synchronized (writer) {
            try {
                writer.write(JsonUtil.stringify(request.toMap()));
                writer.newLine();
                writer.flush();
            } catch (IOException exception) {
                pendingRequests.remove(request.getRequestId());
                throw new CompletionException(exception);
            }
        }
        try {
            ApiMessage response = future.get(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            if (!response.isSuccess()) {
                throw new IllegalStateException(response.getMessage());
            }
            return response;
        } catch (Exception exception) {
            throw new IllegalStateException("Request failed: " + exception.getMessage(), exception);
        } finally {
            pendingRequests.remove(request.getRequestId());
        }
    }

    public void addEventListener(Consumer<ApiMessage> listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(Consumer<ApiMessage> listener) {
        eventListeners.remove(listener);
    }

    private void listen() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> values = (Map<String, Object>) JsonUtil.parse(line);
                ApiMessage message = ApiMessage.fromMap(values);
                if ("RESPONSE".equals(message.getMessageType())) {
                    CompletableFuture<ApiMessage> future = pendingRequests.get(message.getRequestId());
                    if (future != null) {
                        future.complete(message);
                    }
                    continue;
                }
                eventListeners.forEach(listener -> listener.accept(message));
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
