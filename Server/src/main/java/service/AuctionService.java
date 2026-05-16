package service;
//xem xét xóa
import dao.DAOAuction_Items;
import model.Items.Item;
import model.auction.Auction;
import model.auction.AuctionStatus;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.UUID;

public class AuctionService {
    private final DAOAuction_Items auctionDAO = DAOAuction_Items.getInstance();

    // Lấy thông tin đấu giá và tự động kích hoạt nếu đến giờ
    public Auction getAuction(Item item) {
        Auction auction = auctionDAO.selectByItemId(item);

        if (auction == null) {
            auction = new Auction(UUID.randomUUID().toString(), item, item.getSellerId(), Instant.now());
            // PHẢI LƯU XUỐNG DB NGAY LẬP TỨC!
            auctionDAO.Insert(auction, item);
        } else {
            auction.setItem(item);
            // Logic tự động start nếu đến giờ
            if (auction.getStatus() == AuctionStatus.OPEN &&
                    Instant.now().isAfter(item.getAuctionStartTime())) {
                auction.setStatus(AuctionStatus.RUNNING);
                auctionDAO.Update_Status(auction, item, AuctionStatus.RUNNING);
            }
        }
        return auction;
    }

    public static AuctionStatus syncAuctionStatus(Auction auction) {
        if (auction == null || auction.getItem() == null) {
            return null;
        }
        return auction.getStatus();
    }

    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}


