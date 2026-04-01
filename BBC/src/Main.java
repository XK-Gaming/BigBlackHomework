import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        AuctionPlatform platform = new AuctionPlatform();

        Seller seller = new Seller("U1", "Nguyen Seller", "seller01", "seller1");
        Bidder bidderA = new Bidder("U2", "Tran Bidder A", "bidderA", "secretA");
        Bidder bidderB = new Bidder("U3", "Le Bidder B", "bidderB", "secretB");
        Admin admin = new Admin("U4", "Pham Admin", "admin01", "admin01");

        platform.registerUser(seller);
        platform.registerUser(bidderA);
        platform.registerUser(bidderB);
        platform.registerUser(admin);

        Item laptop = new Electronics(
                "I1",
                "Gaming Laptop",
                "RTX laptop, 32GB RAM",
                1000.0,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(30),
                "Lenovo"
        );

        Auction auction = new Auction("A1", laptop, seller);
        platform.addAuction(auction);

        auction.updateStatusByTime();
        auction.placeBid(bidderA, 1100.0);
        auction.placeBid(bidderB, 1250.0);

        seller.printInfo();
        laptop.printInfo();
        auction.printInfo();
        System.out.println(auction.getWinnerSummary());
    }
}
