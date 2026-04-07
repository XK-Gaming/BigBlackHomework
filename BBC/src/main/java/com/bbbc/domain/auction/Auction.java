package com.bbbc.domain.auction;

import com.bbbc.domain.item.Item;
import com.bbbc.domain.observer.AuctionObserver;
import com.bbbc.domain.observer.AuctionUpdate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;

public final class Auction {
    private final String id;
    private final Item item;
    private volatile AuctionState state;
    private volatile String leaderBidderId;
    private volatile String winnerBidderId;
    private final List<BidTransaction> bidHistory;
    private final Map<String, AutoBidConfig> autoBidConfigs;
    private final List<AuctionObserver> observers;
    private final ReentrantLock lock;

    public Auction(String id, Item item) {
        this.id = id;
        this.item = item;
        this.state = AuctionState.OPEN;
        this.bidHistory = new ArrayList<>();
        this.autoBidConfigs = new ConcurrentHashMap<>();
        this.observers = new CopyOnWriteArrayList<>();
        this.lock = new ReentrantLock(true);
    }

    public String getId() {
        return id;
    }

    public Item getItem() {
        return item;
    }

    public AuctionState getState() {
        return state;
    }

    public void setState(AuctionState state) {
        this.state = state;
    }

    public String getLeaderBidderId() {
        return leaderBidderId;
    }

    public void setLeaderBidderId(String leaderBidderId) {
        this.leaderBidderId = leaderBidderId;
    }

    public String getWinnerBidderId() {
        return winnerBidderId;
    }

    public void setWinnerBidderId(String winnerBidderId) {
        this.winnerBidderId = winnerBidderId;
    }

    public List<BidTransaction> getBidHistory() {
        return bidHistory;
    }

    public Map<String, AutoBidConfig> getAutoBidConfigs() {
        return autoBidConfigs;
    }

    public List<AuctionObserver> getObservers() {
        return observers;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public Instant getStartTime() {
        return item.getAuctionStartTime();
    }

    public Instant getEndTime() {
        return item.getAuctionEndTime();
    }

    public void extendEndTime(Instant newEndTime) {
        item.updateAuctionEndTime(newEndTime);
    }

    public void registerObserver(AuctionObserver observer) {
        observers.add(observer);
    }

    public void unregisterObserver(AuctionObserver observer) {
        observers.remove(observer);
    }

    public void addBid(BidTransaction bid) {
        bidHistory.add(bid);
        item.updateCurrentHighestPrice(bid.getAmount());
        leaderBidderId = bid.getBidderId();
    }

    public void notifyObservers(String message) {
        AuctionUpdate update = new AuctionUpdate(
                id,
                item.getName(),
                item.getCurrentHighestPrice(),
                leaderBidderId,
                state,
                item.getAuctionEndTime(),
                message
        );
        observers.forEach(observer -> observer.onAuctionUpdated(update));
    }
}
