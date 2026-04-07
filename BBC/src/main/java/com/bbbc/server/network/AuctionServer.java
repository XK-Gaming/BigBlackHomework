package com.bbbc.server.network;

import com.bbbc.server.controller.AuctionController;
import com.bbbc.server.service.AuctionManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuctionServer {
    private final int port;
    private final AuctionController controller;
    private final ExecutorService clientPool;

    public AuctionServer(int port) {
        this.port = port;
        this.controller = new AuctionController(AuctionManager.getInstance());
        this.clientPool = Executors.newCachedThreadPool();
    }

    public void start() throws IOException {
        AuctionManager.getInstance().start();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                clientPool.submit(new ClientHandler(clientSocket, controller));
            }
        }
    }
}
