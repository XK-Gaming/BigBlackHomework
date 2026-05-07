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

/**
 * Service đấu giá dùng trong nhánh JavaFX UI.
 *
 * Trách nhiệm class: lấy/tạo Auction cho một Item, xử lý bid trực tiếp từ UI, và format giá.
 * NOTE: Luồng socket server chủ yếu dùng service.UserService thay vì class này.
 */
public class AuctionService {
    /** DAO thao tác bảng auction_items. */
    private final DAOAution_Items auctionDAO = DAOAution_Items.getInstance();
    /** DAO thao tác bảng items. */
    private final DAOItems itemDAO = DAOItems.getInstance();

    // Lấy thông tin đấu giá và tự động kích hoạt nếu đến giờ
    /**
     * Precondition: item khác null và item.databaseId đã có dữ liệu nếu cần truy vấn DB.
     * Postcondition: Method trả về Auction hiện có của item; nếu chưa có thì tạo mới và insert DB.
     * NOTE: Nếu auction đã tồn tại, method có thể tự chuyển OPEN sang RUNNING khi đã đến giờ bắt đầu.
     */
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
    /**
     * Precondition: auction, item và user khác null; priceInput là chuỗi số tiền user nhập.
     * Postcondition: Nếu hợp lệ, cập nhật giá hiện tại, leading bidder, auction_items và items.
     * Method trả "SUCCESS" nếu thành công, ngược lại trả message lỗi để UI hiển thị.
     * NOTE: Chỉ cho đặt giá khi auction.getStatus() là RUNNING.
     */
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

    /**
     * Precondition: price là số tiền cần hiển thị.
     * Postcondition: Method trả về chuỗi tiền đã format kèm đơn vị VNĐ.
     */
    public String formatPrice(double price) {
        return new DecimalFormat("#,###").format(price) + " VNĐ";
    }
}


