package com.bbbc.server.repository;

import com.bbbc.domain.auction.Auction;

import java.util.List;
import java.util.Optional;

public interface AuctionRepository {
    Auction save(Auction auction);

    Optional<Auction> findById(String id);

    List<Auction> findAll();

    void deleteById(String id);
}
