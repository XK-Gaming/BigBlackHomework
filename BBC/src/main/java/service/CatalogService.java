package service;

import java.util.Comparator;
import java.util.List;

import model.auction.Auction;
import model.auction.AuctionManager;
import model.auction.AuctionStatus;
import repository.ItemMediaRepository;
import service.dto.CatalogItemView;

public final class CatalogService {
    private final AuctionManager auctionManager;
    private final ItemMediaRepository itemMediaRepository;

    public CatalogService(AuctionManager auctionManager, ItemMediaRepository itemMediaRepository) {
        if (auctionManager == null) {
            throw new IllegalArgumentException("AuctionManager must not be null.");
        }
        if (itemMediaRepository == null) {
            throw new IllegalArgumentException("ItemMediaRepository must not be null.");
        }
        this.auctionManager = auctionManager;
        this.itemMediaRepository = itemMediaRepository;
    }

    public List<CatalogItemView> listCatalogItems() {
        return auctionManager.getAuctions().stream()
                .filter(auction -> auction.getStatus() != AuctionStatus.CANCELED)
                .sorted(Comparator.comparing(auction -> auction.getItem().getEndTime()))
                .map(this::toCatalogItemView)
                .toList();
    }

    private CatalogItemView toCatalogItemView(Auction auction) {
        String imagePath = itemMediaRepository.findImagePath(auction.getItem().getId());
        return new CatalogItemView(auction.getItem(), imagePath);
    }
}
