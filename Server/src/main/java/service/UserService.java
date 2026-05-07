package service;

import dao.DAOAution_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service nghiệp vụ chính dùng bởi các socket handler.
 *
 * Trách nhiệm class: gom logic giữa handler và DAO để handler không phải biết chi tiết
 * truy vấn database của user, item và auction.
 */
public class UserService {
    /** DAO thao tác bảng khach. */
    private DAOUser userDAO = DAOUser.getInstance();
    /** DAO thao tác bảng items. */
    private DAOItems itemDAO = DAOItems.getInstance();
    /** DAO thao tác bảng auction_items. */
    private DAOAution_Items auctionDAO = DAOAution_Items.getInstance();


    /**
     * Precondition: username và password được gửi từ request LOGIN.
     * Postcondition: Method trả về User tương ứng nếu thông tin đăng nhập hợp lệ; ngược lại trả null.
     * NOTE: Hiện tại mật khẩu đang được so sánh dạng plain text.
     */
    public User loginAndGetUser(String username, String password) {
        User user = userDAO.selectByUsername(username, password);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
        /**
         * Precondition: user có username, password, name, address/email và role.
         * Postcondition: Method trả về response map success=false nếu username đã tồn tại;
         * nếu chưa tồn tại thì insert user và trả success=true.
         * NOTE: DAOUser.Insert() hiện luôn return 0 dù có thực thi insert.
         */
        public Map<String, Object> register(User user) {
            Map<String, Object> response = new HashMap<>();

            if (DAOUser.selectByUsername(user.getUsername())) {
                response.put("success", false);
                response.put("message", "Tài khoản đã tồn tại");
                return response;
            }

            DAOUser.getInstance().Insert(user);
            response.put("success", true);
            response.put("message", "Đăng ký tài khoản thành công");
            return response;
        }


    /**
     * Precondition: item có seller id, giá, loại item, ảnh và khoảng thời gian đấu giá.
     * Postcondition: Insert item vào bảng items; tạo thêm một dòng auction_items trạng thái OPEN.
     * Method trả về true sau khi gọi DAO.
     */
    public boolean creater_item(Item item) {
        itemDAO.Insert(item);
        Auction auction = new Auction("1", item, item.getSellerId(), item.getAuctionStartTime());
        auctionDAO.Insert(auction, item);
        return true;
    }

    /**
     * Precondition: Có thể tạo kết nối database.
     * Postcondition: Method trả về toàn bộ item từ DAOItems.selectedAll(), hoặc null nếu DAO lỗi.
     */
    public ArrayList<Item> select_items() {
        return itemDAO.selectAll();
    }

    /**
     * Precondition: itemId là chuỗi id item hợp lệ trong database.
     * Postcondition: Method trả về auction gắn với item, hoặc null nếu item/auction không tồn tại.
     */
    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        return auctionDAO.selectByItemId(item);
    }
    /**
     * Precondition: itemId và userid xác định màn hình auction user đang xem.
     * Postcondition: Phiên bản hiện tại không thay đổi state trong database.
     * Method không trả về giá trị.
     * NOTE: Việc track item đang xem hiện nằm ở ClientHandler.setViewingItemId().
     */
    public void SetAuctionByItemId(String itemId, String userid) {
        // Không cần cập nhật user status nữa
        // Hàm này chỉ là để tracking/logging lần view của user
        // Dữ liệu auction sẽ được fetch lên khi client request GET_AUCTION
    }

    /**
     * Precondition: itemId trỏ tới item tồn tại, bidderId là user đặt giá, và amount lớn hơn
     * giá cao nhất hiện tại.
     * Postcondition: Nếu thành công, cập nhật items.currentHighestBid và auction_items.currentPrice,
     * leadingbider, bidHistory. Method trả về amount đã được chấp nhận.
     * NOTE: Method trả -1 khi validate fail, không tìm thấy item/auction, hoặc update DB lỗi.
     */
    public double processBid(String itemId, String bidderId, double amount) {
        Item item = itemDAO.selectById(itemId);
        int updatedRows = itemDAO.Update(item);
        Auction auction = auctionDAO.selectByItemId(item);

        try {
            List<model.auction.BidTransaction> newHistory = new ArrayList<>(auction.getBidHistory());

            // Tạo transaction ID mới
            String transactionId = "BID-" + System.currentTimeMillis() + "-" + bidderId;
            model.auction.BidTransaction newBid = new model.auction.BidTransaction(
                transactionId,
                bidderId,
                amount,
                java.time.Instant.now()
            );
            newHistory.add(newBid);
            auction.setbidHistory(newHistory);
            int updateResult = auctionDAO.Update(auction, item.getDatabaseId(), bidderId, amount);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        return amount;
    }

    /**
     * Precondition: itemId trỏ tới item tồn tại và status là tên AuctionStatus hợp lệ.
     * Postcondition: Cập nhật auction_items.status cho item nếu auction tồn tại.
     * Method không trả về giá trị.
     * NOTE: Tham số auctionId hiện chưa được sử dụng.
     */
    public void updateAuctionStatus(String auctionId, String itemId, String status) {
        Item item = itemDAO.selectById(itemId);
        if (item != null) {
            AuctionStatus auctionStatus = AuctionStatus.valueOf(status);
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setStatus(auctionStatus);
                auctionDAO.Update_Status(auction, item, auctionStatus);
            }
        }
    }

    /**
     * Precondition: Có thể tạo kết nối database.
     * Postcondition: Method trả về toàn bộ dòng auction_items đã map sang Auction object.
     */
    public List<Auction> getAllAuctions() {
        return auctionDAO.selectAll();
    }

    /**
     * Precondition: username tồn tại và field thuộc nhóm field profile được hỗ trợ.
     * Postcondition: User object trong bộ nhớ được sửa và DAOUser.Update(user) được gọi.
     * Method trả true nếu user tồn tại và field hợp lệ; ngược lại trả false.
     * NOTE: DAOUser.Update() hiện là stub nên thay đổi chưa được lưu xuống DB.
     */
    public boolean updateUser(String username, String field, String value) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        switch (field) {
            case "name":
                user.setName(value);
                break;
            case "phone":
                break;
            case "address":
                user.setAddress(value);
                break;
            default:
                return false;
        }

        userDAO.Update(user);
        return true;
    }

    /**
     * Precondition: username tồn tại, oldPassword là mật khẩu hiện tại, và newPassword được truyền vào.
     * Postcondition: Password trong User object được đổi và DAOUser.Update(user) được gọi.
     * Method trả true chỉ khi user tồn tại và oldPassword khớp.
     * NOTE: DAOUser.Update() hiện là stub nên thay đổi chưa được lưu xuống DB.
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }

        user.setPassword(newPassword);
        userDAO.Update(user);
        return true;
    }

    /**
     * Precondition: username xác định user yêu cầu logout.
     * Postcondition: Phiên bản hiện tại chưa thay đổi state nào.
     * Method không trả về giá trị.
     * NOTE: Chưa gọi AuctionServer.removeOnlineClient(username) tại đây.
     */
    public void logout(String username) {
    }
public void PaymentAccount(String username, Double money) {
        userDAO.UpdateBalance(username, money);
}}
