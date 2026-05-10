package service;

import dao.DAOAution_Items;
import dao.DAOItems;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.exception.BidRejectedException;

import java.text.DecimalFormat;
import java.time.Instant;

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

    /**
     * Xử lý đặt giá (luồng tương tự {@link UserService#processBid} nhưng nhận object đã có sẵn).
     *
     * @throws BidRejectedException khi dữ liệu không hợp lệ hoặc ghi DB thất bại
     */
    public void processBid(Auction auction, Item item, User user, String priceInput) {
        final double amount;
        try {
            amount = Double.parseDouble(priceInput);
        } catch (NumberFormatException e) {
            throw new BidRejectedException(BidRejectedException.Reason.INVALID_INPUT,
                    "Vui lòng nhập số tiền hợp lệ.", e);
        }

        if (amount <= item.getCurrentHighestPrice()) {
            throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                    "Giá đặt phải cao hơn giá hiện tại!");
        }

        if (auction.getStatus() != AuctionStatus.RUNNING) {
            throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                    "Phiên đấu giá hiện không diễn ra.");
        }

        item.setCurrentHighestPrice(amount);
        auction.setLeadingBidder(user.getUsername());
        int rows = auctionDAO.Update(auction, item.getDatabaseId(), user.getUsername(), amount);
        if (rows <= 0) {
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Không thể lưu đấu giá.");
        }
        if (itemDAO.Update(item) <= 0) {
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Không thể cập nhật giá sản phẩm.");
        }
    }
    /**
     * Đồng bộ trạng thái phiên theo thời gian (logic nằm trong {@link Auction#getStatus()}).
     *
     * @return Trạng thái sau khi đã áp dụng quy tắc thời gian, hoặc {@code null} nếu {@code auction} / {@code item} thiếu.
     */
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


