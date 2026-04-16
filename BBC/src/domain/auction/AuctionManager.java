package domain.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import domain.factory.ItemFactory;
import domain.factory.ItemType;
import domain.item.Item;
import domain.user.Bidder;
import domain.user.Seller;

/**
 * Manager trung tam cho luong dau gia trong bo nho:
 * tao auction va dieu phoi cac thao tac chinh lien quan den auction.
 */
public final class AuctionManager {

    private final Map<String, Auction> auctions;

    private AuctionManager() {
        this.auctions = new LinkedHashMap<>();
    }

    public static AuctionManager getInstance() {
        // Holder pattern tao singleton theo yeu cau va an toan voi nhieu thread.
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public Auction createAuction(
            String auctionId,
            String itemId,
            Seller seller,
            ItemType type,
            String name,
            String description,
            double startingPrice,
            String extraInfo,
            Instant startTime,
            Instant endTime
    ) {
        if (seller == null) {
            throw new IllegalArgumentException("Seller khong duoc null.");
        }
        validateAuctionId(auctionId);

        // AuctionManager dung factory de tao dung loai item, manager khong tu new tung subclass.
        Item item = ItemFactory.create(
                type,
                itemId,
                name,
                description,
                startingPrice,
                startTime,
                endTime,
                seller.getId(),
                buildAttributes(type, extraInfo)
        );

        Auction auction = new Auction(auctionId, item, seller);
        auctions.put(auction.getId(), auction);
        return auction;
    }

    public void addAuction(Auction auction) {
        if (auction == null) {
            throw new IllegalArgumentException("Auction khong duoc null.");
        }
        validateAuctionId(auction.getId());
        auctions.put(auction.getId(), auction);
    }

    private Map<String, Object> buildAttributes(ItemType type, String extraInfo) {
        if (type == null) {
            throw new IllegalArgumentException("Item type khong duoc null.");
        }
        if (extraInfo == null || extraInfo.isBlank()) {
            throw new IllegalArgumentException("Extra info khong duoc rong.");
        }

        // Chuyen input don gian thanh bo attributes ma ItemFactory can.
        return switch (type) {
            case ART -> Map.of("artist", extraInfo);
            case ELECTRONICS -> Map.of("brand", extraInfo);
            case VEHICLE -> Map.of("manufacturer", extraInfo);
        };
    }

    public void startAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.start();
    }

    public void placeBid(String auctionId, Bidder bidder, double amount) {
        Auction auction = requireAuction(auctionId);
        auction.placeBid(bidder, amount);
    }

    public void watchAuction(String auctionId, Bidder bidder) {
        if (bidder == null) {
            throw new IllegalArgumentException("Bidder khong duoc null.");
        }
        Auction auction = requireAuction(auctionId);
        bidder.watchAuction(auction);
    }

    public void cancelAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.cancel();
    }

    public void removeAuction(String auctionId) {
        Auction removedAuction = auctions.remove(auctionId);
        if (removedAuction == null) {
            throw new IllegalArgumentException("Khong tim thay auction: " + auctionId);
        }
    }

    public List<Auction> getAuctions() {
        return new ArrayList<>(auctions.values());
    }

    public Auction findAuctionById(String id) {
        return auctions.get(id);
    }

    private Auction requireAuction(String auctionId) {
        // Ham helper de tranh lap lai kiem tra null trong start/placeBid/cancel.
        Auction auction = findAuctionById(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Khong tim thay auction: " + auctionId);
        }
        return auction;
    }

    private void validateAuctionId(String auctionId) {
        if (auctionId == null || auctionId.isBlank()) {
            throw new IllegalArgumentException("Auction id khong duoc rong.");
        }
        if (auctions.containsKey(auctionId)) {
            throw new IllegalArgumentException("Auction id da ton tai: " + auctionId);
        }
    }
}
