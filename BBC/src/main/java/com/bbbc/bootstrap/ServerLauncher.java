package com.bbbc.bootstrap;

import com.bbbc.server.network.AuctionServer;

public final class ServerLauncher {
    private ServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        new AuctionServer(5555).start();
    }
}
