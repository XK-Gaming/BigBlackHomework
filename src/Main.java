import java.time.LocalDateTime;

public class Main {
    public static void main(String[] args) {
        AuctionManager manager = AuctionManager.getInstance();

        Seller seller = (Seller) manager.registerUser("seller", "hung123", "123", "Nguoi ban A");
        Bidder bidder1 = (Bidder) manager.registerUser("bidder", "hung bidder", "123", "Nguoi mua 1");
        Bidder bidder2 = (Bidder) manager.registerUser("bidder", "hung bidder2", "123", "Nguoi mua 2");
        Admin admin = (Admin) manager.registerUser("admin", "admin", "123", "Quan tri vien");
        // Dang ky user qua AuctionManager

        User loginUser = manager.login("hung123", "123");
        System.out.println(loginUser != null
                ? "Dang nhap thanh cong: " + loginUser.printInfo()
                : "Dang nhap that bai");
        // Demo luong register -> login.

        Auction auction = manager.createAuction(
                "AUC1",
                "ITEM1",
                seller,
                ItemType.ELECTRONICS,
                "Laptop Lenovo",
                "demo dau gia",
                10_000_000,
                "rtx 5060",
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(30)
        );
        // Tao san pham bang Factory Method va mo phien dau gia.

        auction.placeBid("BID1", bidder1, 10_500_000);
        auction.placeBid("BID2", bidder2, 11_000_000);
        // Bidder dat gia cao hon gia hien tai.

        auction.finishAuction();
        // Ket thuc phien de xac dinh nguoi thang cuoc.

        System.out.println(seller.printInfo());
        System.out.println(bidder1.printInfo());
        System.out.println(admin.printInfo());
        System.out.println(auction.printInfo());
        for (BidTransaction transaction : auction.getBidTransactions()) {
            System.out.println(transaction.printInfo());
        }
        // In ket qua de kiem tra ke thua, da hinh va logic dau gia co ban.
    }
}
