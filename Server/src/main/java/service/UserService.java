package service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dao.DAOAuction_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.exception.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class UserService {
    // ✅ SỬA LỖI #1: Dùng Guava Cache thay vì ConcurrentHashMap để tự động cleanup
    // Lock sẽ tự động bị xóa sau 10 phút không sử dụng
    private static final Cache<String, ReentrantLock> itemLocks = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
          //  .weakValues()  Tự động giải phóng khi không còn reference **xem xet**
            .build();

    private final DAOUser userDAO = DAOUser.getInstance();
    private final DAOItems itemDAO = DAOItems.getInstance();
    private final DAOAuction_Items auctionDAO = DAOAuction_Items.getInstance();

    /**
     * ✅ Thread-safe: Lấy lock cho item, tự động tạo nếu chưa có
     */
    private ReentrantLock getLockForItem(String itemId) {
        try {
            return itemLocks.get(itemId, () -> new ReentrantLock(true));
        } catch (Exception e) {
            // Fallback nếu cache có vấn đề
            return new ReentrantLock(true);
        }
    }

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
     * ✅ SỬA LỖI #2: Xử lý race condition bằng cách rely vào UNIQUE constraint của DB
     * @throws ConflictException khi username đã tồn tại
     */
    public Map<String, Object> register(User user) {
        try {
            // Thử insert trực tiếp - DB sẽ từ chối nếu trùng username (UNIQUE constraint)
            DAOUser.getInstance().Insert(user);

            Map<String, Object> response = new HashMap<>();
            response.put("success", "TRUE");
            return response;

        } catch (SQLException e) {
            // Bắt lỗi duplicate key từ DB
            if (e.getMessage() != null &&
                    (e.getMessage().contains("Duplicate entry") ||
                            e.getMessage().contains("UNIQUE constraint"))) {
                throw new ConflictException("Tên đăng nhập đã được sử dụng.");
            }
            // Lỗi khác
            throw new PersistenceException("Không thể tạo tài khoản: " + e.getMessage());
        }
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
        // Tracking only
    }

    /**
     * ✅ SỬA LỖI #4: Tối ưu locking strategy cho Single Server
     * - Giữ ReentrantLock ở tầng Java
     * - Bỏ SERIALIZABLE isolation level (quá chặt)
     * - Bỏ SELECT FOR UPDATE (không cần vì đã có ReentrantLock)
     *
     * @return Giá chấp nhận sau khi đặt thành công
     * @throws BidRejectedException khi từ chối đặt giá hoặc lỗi lưu trữ
     */
    public double processBid(String itemId, String bidderId, double amount) {
        ReentrantLock lock = getLockForItem(itemId);
        lock.lock(); // Chỉ 1 thread được xử lý item này tại một thời điểm

        Connection con = null;
        try {
            con = database.JDBCUtil.getConnection();
            con.setAutoCommit(false);

            // ✅ Giữ isolation level mặc định (READ_COMMITTED hoặc REPEATABLE_READ)
            // Không cần SERIALIZABLE vì ReentrantLock đã bảo vệ rồi

            // ✅ SELECT bình thường, không cần FOR UPDATE
            Item item = itemDAO.selectById(con, itemId);

            if (item == null) {
                throw new NotFoundException("item", "Không tìm thấy sản phẩm.");
            }

            if (bidderId != null && item.getSellerId() != null &&
                    bidderId.equals(item.getSellerId())) {
                throw new BidRejectedException(BidRejectedException.Reason.SELLER_BID,
                        "Người bán không thể đặt giá cho sản phẩm của mình.");
            }

            // Kiểm tra giá trong transaction
            if (amount <= item.getCurrentHighestPrice()) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Giá đặt phải cao hơn giá hiện tại: " + item.getCurrentHighestPrice());
            }

            Auction auction = auctionDAO.selectByItemId(con, item);
            if (auction == null) {
                throw new NotFoundException("auction", "Không tìm thấy phiên đấu giá.");
            }
            auction.setItem(item);

            if (auction.getStatus() != AuctionStatus.RUNNING) {
                throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                        "Phiên đấu giá hiện không diễn ra hoặc đã kết thúc.");
            }

            // Tạo bid transaction mới
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

            // BƯỚC A: Cập nhật bảng Auction
            int auctionResult = auctionDAO.Update(con, auction, item.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            // BƯỚC B: Cập nhật bảng Items
            item.setCurrentHighestPrice(amount);
            int itemResult = itemDAO.Update(con, item);
            if (itemResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng items.");
            }

            // Commit transaction
            con.commit();
            return amount;

        } catch (BidRejectedException | NotFoundException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw e;
        } catch (Exception e) {
            System.err.println("❌ processBid: Lỗi: " + e.getMessage());
            if (con != null) {
                try {
                    con.rollback();
                    System.err.println("🔄 Đã Rollback.");
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Lỗi lưu dữ liệu. Vui lòng thử lại.", e);
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
            lock.unlock(); // ✅ LUÔN unlock
        }
    }

    public List<Auction> getAllAuctions() {
        return auctionDAO.selectAll();
    }

    /**
     * ✅ SỬA LỖI #3: Update trực tiếp qua SQL thay vì lấy object về
     * Tránh race condition khi 2 thread cùng update 2 field khác nhau
     */
    public boolean updateUser(String username, String field, String value) {
        Connection con = null;
        try {
            con = database.JDBCUtil.getConnection();
            String sql;

            switch (field) {
                case "name":
                    sql = "UPDATE users SET name = ? WHERE username = ?";
                    break;
                case "phone":
                    sql = "UPDATE users SET phone = ? WHERE username = ?";
                    break;
                case "address":
                    sql = "UPDATE users SET address = ? WHERE username = ?";
                    break;
                default:
                    return false;
            }

            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, value);
            ps.setString(2, username);

            int result = ps.executeUpdate();
            ps.close();

            return result > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    /**
     * ✅ SỬA LỖI #3: Update password trực tiếp qua SQL với kiểm tra atomic
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Connection con = null;
        try {
            con = database.JDBCUtil.getConnection();

            // ✅ Update atomic: chỉ update nếu password hiện tại khớp với oldPassword
            String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, oldPassword);

            int result = ps.executeUpdate();
            ps.close();

            // Nếu result = 0 nghĩa là không có row nào bị update
            // => username không tồn tại HOẶC password cũ sai
            return result > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public void logout(String username) {
        // Cleanup session nếu cần
    }
}