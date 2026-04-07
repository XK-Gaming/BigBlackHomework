package com.bbbc.server.repository;

import com.bbbc.domain.auction.Auction;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAuctionRepository implements AuctionRepository {
    private final ConcurrentHashMap<String, Auction> auctions = new ConcurrentHashMap<>();

    @Override
    public Auction save(Auction auction) {
        auctions.put(auction.getId(), auction);
        return auction;
    }

    @Override
    public Optional<Auction> findById(String id) {
        return Optional.ofNullable(auctions.get(id));
    }

    @Override
    public List<Auction> findAll() {
        return auctions.values().stream()
                .sorted((left, right) -> left.getEndTime().compareTo(right.getEndTime()))
                .toList();
    }

    @Override
    public void deleteById(String id) {
        auctions.remove(id);
    }
}
