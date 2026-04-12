package domain.auction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import domain.exception.AuctionException;
import domain.user.User;

public class AuctionPlatform {
    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Auction> auctions = new LinkedHashMap<>();

    public void registerUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null.");
        }
        if (users.containsKey(user.getUsername())) {
            throw new AuctionException("Username already exists.");
        }
        users.put(user.getUsername(), user);
    }

    public User login(String username, String password) {
        User user = users.get(username);
        if (user == null || !user.authenticate(password)) {
            throw new AuctionException("Invalid username or password.");
        }
        return user;
    }

    public void addAuction(Auction auction) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction must not be null.");
        }
        auctions.put(auction.getId(), auction);
    }

    public Auction getAuction(String auctionId) {
        Auction auction = auctions.get(auctionId);
        if (auction == null) {
            throw new AuctionException("Auction not found.");
        }
        return auction;
    }

    public void removeAuction(String auctionId) {
        if (auctions.remove(auctionId) == null) {
            throw new AuctionException("Auction not found.");
        }
    }

    public Collection<Auction> getAllAuctions() {
        return auctions.values();
    }
}
