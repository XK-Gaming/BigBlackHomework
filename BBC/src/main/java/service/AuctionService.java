package service;

import model.Items.Item;
import model.User.Bidder;
import model.auction.Auction;
import model.auction.AuctionManager;
import model.exception.AuctionNotFoundException;
import model.exception.ValidationException;
import repository.ItemMediaRepository;
import service.dto.AuctionDetailView;

public final class AuctionService {
    private final AuctionManager auctionManager;
    private final ItemMediaRepository itemMediaRepository;

    public AuctionService(AuctionManager auctionManager, ItemMediaRepository itemMediaRepository) {
        if (auctionManager == null) {
            throw new IllegalArgumentException("AuctionManager must not be null.");
        }
        if (itemMediaRepository == null) {
            throw new IllegalArgumentException("ItemMediaRepository must not be null.");
        }
        this.auctionManager = auctionManager;
        this.itemMediaRepository = itemMediaRepository;
    }

    public AuctionDetailView getAuctionDetailByItem(Item item) {
        if (item == null) {
            throw new AuctionNotFoundException("selected-item");
        }
        return getAuctionDetailByItemId(item.getId());
    }

    public AuctionDetailView getAuctionDetailByItemId(String itemId) {
        Auction auction = findAuctionByItemId(itemId);
        return new AuctionDetailView(auction, itemMediaRepository.findImagePath(auction.getItem().getId()));
    }

    public AuctionDetailView placeBid(Item item, Bidder bidder, String amountText) {
        if (item == null) {
            throw new AuctionNotFoundException("selected-item");
        }
        if (bidder == null) {
            throw new ValidationException("Bidder is required.");
        }
        double amount = parseBidAmount(amountText);
        Auction auction = findAuctionByItemId(item.getId());
        auction.placeBid(bidder, amount);
        return new AuctionDetailView(auction, itemMediaRepository.findImagePath(item.getId()));
    }

    public Auction findAuctionByItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new AuctionNotFoundException("selected-item");
        }
        return auctionManager.getAuctions().stream()
                .filter(auction -> auction.getItem().getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AuctionNotFoundException(itemId));
    }

    private double parseBidAmount(String amountText) {
        if (amountText == null || amountText.isBlank()) {
            throw new ValidationException("Bid amount is required.");
        }
        String normalizedAmount = amountText.replace(",", "").trim();
        try {
            return Double.parseDouble(normalizedAmount);
        } catch (NumberFormatException exception) {
            throw new ValidationException("Bid amount is invalid.");
        }
    }
}
