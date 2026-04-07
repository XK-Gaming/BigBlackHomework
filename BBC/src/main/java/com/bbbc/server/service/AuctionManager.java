package com.bbbc.server.service;

import com.bbbc.domain.factory.ItemFactory;
import com.bbbc.server.repository.AuctionRepository;
import com.bbbc.server.repository.InMemoryAuctionRepository;
import com.bbbc.server.repository.InMemoryUserRepository;
import com.bbbc.server.repository.UserRepository;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class AuctionManager {
    private static final AuctionManager INSTANCE = new AuctionManager();

    private final UserRepository userRepository;
    private final AuctionRepository auctionRepository;
    private final AuthenticationService authenticationService;
    private final AuctionService auctionService;
    private final ScheduledExecutorService scheduler;

    private AuctionManager() {
        this.userRepository = new InMemoryUserRepository();
        this.auctionRepository = new InMemoryAuctionRepository();
        this.authenticationService = new AuthenticationService(userRepository);
        this.auctionService = new AuctionService(auctionRepository, userRepository, new ItemFactory());
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        DemoDataSeeder.seed(authenticationService, auctionService);
    }

    public static AuctionManager getInstance() {
        return INSTANCE;
    }

    public AuthenticationService getAuthenticationService() {
        return authenticationService;
    }

    public AuctionService getAuctionService() {
        return auctionService;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(auctionService::closeExpiredAuctions, 1, 1, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }
}
