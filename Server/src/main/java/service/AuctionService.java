package service;

import dao.DAOAution_Items;
import dao.DAOItems;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;

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
                auctionDAO.Update_Status(item,AuctionStatus.RUNNING);
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
            itemDAO.Update(item);

            return "SUCCESS";
        } catch (NumberFormatException e) {
            return "Vui lòng nhập số tiền hợp lệ.";
        }
    }

   public static Object updateStatusByTime(Auction auction) {
        Instant now = Instant.now();

        // 1. Nếu đấu giá đã bị hủy hoặc đã thanh toán thì không tự động đổi nữa, hoa
        if (auction.getStatus() == null ||auction.getStatus() == AuctionStatus.CANCELED || auction.getStatus() == AuctionStatus.PAID) {
            return null;
        }

        // 2. Kiểm tra mốc kết thúc (Ưu tiên kiểm tra kết thúc trước)
        if (now.isAfter(auction.getItem().getAuctionEndTime()) || now.equals(auction.getItem().getAuctionEndTime())) {
            if (auction.getStatus() != AuctionStatus.FINISHED) {
                 auction.setStatus(AuctionStatus.FINISHED);
                // Gọi DAO để đồng bộ xuống Database ngay lập tức
                DAOAution_Items.getInstance().Update_Status(auction.getItem(), AuctionStatus.FINISHED);
            }
        }
        // 3. Kiểm tra mốc bắt đầu
        else if (now.isAfter(auction.getItem().getAuctionStartTime()) || now.equals(auction.getItem().getAuctionStartTime())) {
            if (auction.getStatus() == AuctionStatus.OPEN) {
                auction.setStatus(AuctionStatus.RUNNING);
                // Đồng bộ trạng thái RUNNING xuống Database
                DAOAution_Items.getInstance().Update_Status(auction.getItem(), AuctionStatus.RUNNING);
            }
        }
        // 4. Mặc định vẫn là OPEN nếu chưa tới giờ
        else {
            auction.setStatus(AuctionStatus.OPEN);
        }
    return null;}

    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}


