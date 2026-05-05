package model.auction;

import dao.DAOAution_Items;
import dao.DAOItems;
import model.Items.Item;
import model.User.Bidder;
import model.User.User;
import model.exception.AuctionException;
import model.observer.AuctionObserver;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;

public class AuctionService {
    private final DAOAution_Items auctionDAO = DAOAution_Items.getInstance();
    private final DAOItems itemDAO = DAOItems.getInstance();

    // Lấy thông tin đấu giá và tự động kích hoạt nếu đến giờ
    public Auction getAuction(Item item) {
        Auction auction = auctionDAO.selectByItemId(item);

        if (auction == null) {
            auction = new Auction("1", item, item.getSellerId(), Instant.now());
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

    // Xử lý đặt giá
    public String processBid(Auction auction, Item item, User user, String priceInput) {
        try {
            double amount = Double.parseDouble(priceInput);

            // Quy tắc: Phải cao hơn giá hiện tại
            if (amount <= item.getCurrentHighestPrice()) {
                return "Giá đặt phải cao hơn giá hiện tại!";
            }

            // Quy tắc: Trạng thái phải đang chạy
            if (auction.getStatus() != AuctionStatus.RUNNING) {
                return "Phiên đấu giá hiện không diễn ra.";
            }

            // Cập nhật dữ liệu
            item.setCurrentHighestPrice(amount);
            auction.setLeadingBidder(user.getUsername());

            // Lưu vào DB
            auctionDAO.Update(auction, item.getDatabaseId(), user.getUsername(),amount);
            itemDAO.Update(item, amount);

            return "SUCCESS";
        } catch (NumberFormatException e) {
            return "Vui lòng nhập số tiền hợp lệ.";
        }
    }

    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}


