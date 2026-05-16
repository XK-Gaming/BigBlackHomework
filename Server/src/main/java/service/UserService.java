package service;

import dao.DAOAuction_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.exception.BidRejectedException;
import model.exception.ConflictException;
import model.exception.NotFoundException;
import model.exception.PersistenceException;
import model.exception.UnauthorizedException;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService {
    // Tạo một biến static ConcurrentHashMap trong toàn bộ Server...
    // Dù có nhiều ClientHandler chạy nhiều đối tượng UserService
    // thì vẫn đều chạy một hashmap chung
    private static final Map<String, Object> itemLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private final DAOUser userDAO = DAOUser.getInstance();
    private final DAOItems itemDAO = DAOItems.getInstance();
    private final DAOAuction_Items auctionDAO = DAOAuction_Items.getInstance();


    /**
     * @throws UnauthorizedException khi sai thông tin đăng nhập
     */
    public User loginAndGetUser(String username, String password) {
        User user = userDAO.selectByUsername(username, password);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        throw new UnauthorizedException("Sai tên đăng nhập hoặc mật khẩu.");
    }

    /**
     * @throws ConflictException khi username đã tồn tại
     */
    public Map<String, Object> register(User user) {
        if (userDAO.selectByUsername(user.getUsername())) {
            throw new ConflictException("Tên đăng nhập đã được sử dụng.");
        }
        DAOUser.getInstance().Insert(user);
        Map<String, Object> response = new HashMap<>();
        response.put("success", "TRUE");
        return response;
    }

    /**
     * @throws PersistenceException khi không ghi được sản phẩm hoặc phiên đấu giá
     */
    public void creater_item(Item item) {
        int itemRows = itemDAO.Insert(item);
        if (itemRows <= 0) {
            throw new PersistenceException("Không thể lưu sản phẩm.");
        }
        Auction auction = new Auction("1", item, item.getSellerId(), item.getAuctionStartTime());
        int auctionRows = auctionDAO.Insert(auction, item);
        if (auctionRows <= 0) {
            throw new PersistenceException("Không thể tạo phiên đấu giá.");
        }
    }

    public ArrayList<Item> select_items() {
        return itemDAO.selectAll();
    }

    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        return auctionDAO.selectByItemId(item);
    }
    public void SetAuctionByItemId(String itemId, String userid) {
        // Không cần cập nhật user status nữa
        // Hàm này chỉ là để tracking/logging lần view của user
        // Dữ liệu auction sẽ được fetch lên khi client request GET_AUCTION
    }

    /**
     * @return Giá chấp nhận sau khi đặt thành công ({@code amount}).
     * @throws BidRejectedException khi từ chối đặt giá hoặc lỗi lưu trữ
     */
    public double processBid(String itemId, String bidderId, double amount) {
        Object lock = itemLocks.computeIfAbsent(itemId,k -> new Object());
        // Là khóa the id của item để đảm bảo chỉ một thread được
        // phép xử lý bid cho item đó tại một thời điểm.
        // Cơ chế computeIfAbsent đảm bảo rằng nếu đã có khóa cho itemId
        // thì sẽ trả về khóa đó, nếu chưa có thì sẽ tạo mới và trả về.
        synchronized (lock) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) {
            throw new NotFoundException("item", "Không tìm thấy sản phẩm.");
        }
        if (bidderId != null && item.getSellerId() != null && bidderId.equals(item.getSellerId())) {
            throw new BidRejectedException(BidRejectedException.Reason.SELLER_BID,
                    "Người bán không thể đặt giá cho sản phẩm của mình.");
        }
        if (amount <= item.getCurrentHighestPrice()) {
            throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                    "Giá đặt phải cao hơn giá hiện tại.");
        }

        Auction auction = auctionDAO.selectByItemId(item);
        if (auction == null) {
            throw new NotFoundException("auction", "Không tìm thấy phiên đấu giá.");
        }
        auction.setItem(item);

        // Đồng bộ OPEN/RUNNING/FINISHED theo đồng hồ (và DB khi đổi trạng thái)
        if ( auction.getStatus() != AuctionStatus.RUNNING) {
            throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                    "Phiên đấu giá hiện không diễn ra hoặc đã kết thúc.");
        }
//Thực thi Transaction để đảm bảo tính nguyên tử (Atomic)
        Connection con = null;
        try {
            con = database.JDBCUtil.getConnection();
            con.setAutoCommit(false); // Bắt đầu giao dịch

            List<model.auction.BidTransaction> newHistory = new ArrayList<>(auction.getBidHistory());
            String transactionId = "BID-" + System.nanoTime() + "-" + bidderId;
            model.auction.BidTransaction newBid = new model.auction.BidTransaction(
                transactionId,
                bidderId,
                amount,
                java.time.Instant.now()
            );
            newHistory.add(newBid);
            auction.setBidHistory(newHistory);

            // BƯỚC A: Cập nhật bảng Auction (Lịch sử + Người dẫn đầu)
            // Lưu ý: Gọi hàm Update có truyền Connection 'con'
            int auctionResult = auctionDAO.Update(con, auction, item.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            // BƯỚC B: Cập nhật bảng Items (Giá cao nhất hiển thị)
            item.setCurrentHighestPrice(amount);
            int itemResult = itemDAO.Update(con, item);
            if (itemResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng items.");
            }

            // XÁC NHẬN: Nếu cả 2 lệnh trên thành công, lưu vĩnh viễn vào DB
            con.commit();
            return amount;
        } catch (BidRejectedException | NotFoundException e) {
            // Lỗi nghiệp vụ: Không cần rollback vì chưa ghi gì hoặc rollback tự động nếu cần
            throw e;
        } catch (Exception e) {
            // LỖI HỆ THỐNG: Thực hiện Rollback ngay lập tức
            System.err.println("❌ processBid: Lỗi khi addding BidTransaction: " + e.getMessage());
            if (con != null) {
                try {
                    con.rollback();
                    System.err.println("🔄 Đã Rollback dữ liệu thành công.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Lỗi lưu dữ liệu. Vui lòng thử lại.", e);
        }finally {
            // LUÔN LUÔN giải phóng kết nối
            if (con != null) {
                try {
                    con.setAutoCommit(true); // Trả lại trạng thái mặc định
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
        }
    }
    public List<Auction> getAllAuctions() {
        return auctionDAO.selectAll();
    }

    public boolean updateUser(String username, String field, String value) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        switch (field) {
            case "name":
                user.setName(value);
                break;
            case "phone":
                //them cai nay
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

    public void logout(String username) {
    }
}