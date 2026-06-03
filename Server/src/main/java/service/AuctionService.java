package service;
import dao.DAOItems;
import model.Items.Item;
import model.auction.Auction;
import model.auction.AuctionStatus;
import dao.DAOAuction_Items;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.UUID;

// Nghiệp vụ phiên đấu giá.
public class AuctionService {
    private final DAOAuction_Items auctionDAO = DAOAuction_Items.getInstance();
    private final DAOItems itemDAO = DAOItems.getInstance();
    // Lấy hoặc tạo phiên.
    public Auction getAuction(Item item) {
        Auction auction = auctionDAO.selectByItemId(item);

        if (auction == null) {
            auction = new Auction(UUID.randomUUID().toString(), item, item.getSellerId(), Instant.now());

            auctionDAO.Insert(auction, item);
        } else {
            auction.setItem(item);

            if (auction.getStatus() == AuctionStatus.OPEN &&
                    Instant.now().isAfter(item.getAuctionStartTime())) {
                auction.setStatus(AuctionStatus.RUNNING);
                auctionDAO.Update_Status(auction, item, AuctionStatus.RUNNING);
            }
        }
        return auction;
    }
    // Đồng bộ trạng thái phiên.
    public static AuctionStatus syncAuctionStatus(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            return null;
        }
        return auction.getRawStatus();
    }
    // Định dạng hiển thị.
    public String formatPrice(double price) {
        java.text.DecimalFormatSymbols symbols = java.text.DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(',');
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###", symbols);
        return df.format(price) + " VNĐ";
    }
}
