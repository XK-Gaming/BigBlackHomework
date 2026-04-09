package domain.auction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import domain.factory.ItemFactory;
import domain.factory.ItemType;
import domain.item.Item;
import domain.user.Admin;
import domain.user.Bidder;
import domain.user.Seller;
import domain.user.User;

/**
 * Manager trung tam cho luong dau gia trong bo nho:
 * dang ky user, tao auction va dieu phoi cac thao tac chinh.
 */
public final class AuctionManager {

    private final List<User> users;
    private final List<Auction> auctions;

    private AuctionManager() {
        this.users = new ArrayList<>();
        this.auctions = new ArrayList<>();
    }

    public static AuctionManager getInstance() {
        // Holder pattern tao singleton theo yeu cau va an toan voi nhieu thread.
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final AuctionManager INSTANCE = new AuctionManager();
    }

    public void addUser(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User khong hop le.");
        }
        validateNewUser(user);
        users.add(user);
    }

    public User registerUser(String role, String username, String password, String fullName) {
        String userId = generateUserId();
        String normalizedRole = normalizeRole(role);

        // Role quyet dinh lop User cu the duoc tao ra.
        User user = switch (normalizedRole) {
            case "seller" -> new Seller(userId, fullName, username, password);
            case "bidder" -> new Bidder(userId, fullName, username, password);
            case "admin" -> new Admin(userId, fullName, username, password);
            default -> throw new IllegalArgumentException("Role khong hop le: " + role);
        };

        addUser(user);
        return user;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role khong duoc rong.");
        }
        return role.trim().toLowerCase();
    }

    private String generateUserId() {
        return "U" + (users.size() + 1);
    }

    public User login(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.authenticate(password)) {
                return user;
            }
        }
        return null;
    }

    private void validateNewUser(User newUser) {
        for (User user : users) {
            if (user.getId().equals(newUser.getId())) {
                throw new IllegalArgumentException("Id user da ton tai: " + newUser.getId());
            }
            if (user.getUsername().equalsIgnoreCase(newUser.getUsername())) {
                throw new IllegalArgumentException("Username da ton tai: " + newUser.getUsername());
            }
        }
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
        auctions.add(auction);
        return auction;
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

    public void cancelAuction(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.cancel();
    }

    public List<User> getUsers() {
        return new ArrayList<>(users);
    }

    public List<Auction> getAuctions() {
        return new ArrayList<>(auctions);
    }

    public Auction findAuctionById(String id) {
        for (Auction auction : auctions) {
            if (auction.getId().equals(id)) {
                return auction;
            }
        }
        return null;
    }

    private Auction requireAuction(String auctionId) {
        // Ham helper de tranh lap lai kiem tra null trong start/placeBid/cancel.
        Auction auction = findAuctionById(auctionId);
        if (auction == null) {
            throw new IllegalArgumentException("Khong tim thay auction: " + auctionId);
        }
        return auction;
    }
}
