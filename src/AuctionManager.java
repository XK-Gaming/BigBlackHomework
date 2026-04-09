import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AuctionManager{
    private static AuctionManager instance;

    private final List<User> users;
    private final List<Auction> auctions;

    private AuctionManager(){
        this.users = new ArrayList<>();
        this.auctions = new ArrayList<>();
    }

    public static AuctionManager getInstance(){
        if (instance == null) {
            instance = new AuctionManager();
        }
        return instance;
    }
    //singleton dam bao chi co 1 AuctionManager duy nhat

    public void addUser(User user){
        if (user == null){
            throw new IllegalArgumentException("user không hợp lệ");
        }
        users.add(user);
    }

    public User registerUser(String role, String username, String password, String fullName) {
        String userId = generateUserId();
        String normalizedRole = normalizeRole(role);
        User user;

        switch (normalizedRole) {
            case "seller":
                user = new Seller(userId, username, password, fullName);
                break;
            case "bidder":
                user = new Bidder(userId, username, password, fullName);
                break;
            case "admin":
                user = new Admin(userId, username, password, fullName);
                break;
            default:
                throw new IllegalArgumentException("Role khong hop le: " + role);
        }
        addUser(user);
        return user;
    }
    //factory method de tao user

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role khong duoc rong.");
        }
        return role.trim().toLowerCase();
    }

    private String generateUserId() {
        return "U" + (users.size() + 1);
    }
    //tu dong tao id cho user moi


    public User login(String username, String password) {
        for (User user : users) {
            if (user.getUsername().equals(username) && user.checkPassword(password)) {
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
    //dam bao ko bi trung user

    public Auction createAuction(String auctionId, String itemId, Seller seller, ItemType type, String name, String description,
                                 double startingPrice, String extraInfo,
                                 LocalDateTime startTime, LocalDateTime endTime) {
        Item item = ItemFactory.createItem(itemId, type, seller, name, description, startingPrice, extraInfo);
        Auction auction = new Auction(auctionId, item, startTime, endTime);
        auctions.add(auction);
        return auction;
    }
    //factory method

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
}