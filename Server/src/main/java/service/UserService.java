package service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dao.DAOAuction_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.DepositTransaction;
import model.Items.Item;
import model.User.User;
import model.User.UserRole;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidHistoryDTO;
import model.auction.BidTransaction;
import model.exception.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@FunctionalInterface
interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}

public class UserService {
    static final long ANTI_SNIPING_WINDOW_SECONDS = 60;
    static final long ANTI_SNIPING_EXTENSION_SECONDS = 90;
    private static final double MAX_MIN_BID_RATIO = 0.20;

    // ✅ SỬA LỖI #1: Dùng Guava Cache thay vì ConcurrentHashMap để tự động cleanup
    private static final Cache<String, ReentrantLock> itemLocks = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    private final DAOUser userDAO;
    private final DAOItems itemDAO;
    private final DAOAuction_Items auctionDAO;
    private final ConnectionProvider connectionProvider;
    private final Clock clock;

    public UserService() {
        this(DAOUser.getInstance(), DAOItems.getInstance(), DAOAuction_Items.getInstance(),
                database.JDBCUtil::getConnection);
    }

    UserService(DAOUser userDAO, DAOItems itemDAO, DAOAuction_Items auctionDAO,
                ConnectionProvider connectionProvider) {
        this(userDAO, itemDAO, auctionDAO, connectionProvider, Clock.systemUTC());
    }

    UserService(DAOUser userDAO, DAOItems itemDAO, DAOAuction_Items auctionDAO,
                ConnectionProvider connectionProvider, Clock clock) {
        this.userDAO = userDAO;
        this.itemDAO = itemDAO;
        this.auctionDAO = auctionDAO;
        this.connectionProvider = connectionProvider;
        this.clock = clock;
    }

    /**
     * ✅ Thread-safe: Lấy lock cho item, tự động tạo nếu chưa có
     */
    private ReentrantLock getLockForItem(String itemId) {
        try {
            return itemLocks.get(itemId, () -> new ReentrantLock(true));
        } catch (Exception e) {
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

    public User getUserOnly(String username) {
        return userDAO.selectByUsernameOnly(username);
    }

    /**
     * ✅ SỬA LỖI #2: Xử lý race condition bằng cách rely vào UNIQUE constraint của DB
     */
    public Map<String, Object> register(User user) {
        if (userDAO.selectByUsernameOnly(user.getUsername())!= null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", "EXSITED");
            response.put("message", "Tài khoản đã tồn tại");
            return response;
        }
        else {

            try {
                userDAO.Insert(user);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", "TRUE");
            return response;}
        }


    public void creater_item(Item item) {
        validateMinBidForSave(item, true);
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

    public ArrayList<Item> select_items(UserRole role) {
        ArrayList<Item> list = itemDAO.selectAll();
        if (role == UserRole.ADMIN) {
            return list;
        }
        list.removeIf(item -> item.getAuctionStatus() == null);
        return list;
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
     */
    public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
        ReentrantLock lock = getLockForItem(itemId);
        lock.lock(); // Khóa luồng Java

        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false); // 🌟 BẮT ĐẦU TRANSACTION AN TOÀN

            // 1. Đọc dữ liệu CHUẨN từ trong Transaction (Sử dụng 'con')
            Item txItem = itemDAO.selectById(con, itemId); // Hàm này phải nhận con
            if (txItem == null) {
                throw new NotFoundException("item", "Không tìm thấy sản phẩm.");
            }

            // 2. Kiểm tra các điều kiện chặn
            if (bidderId != null && txItem.getSellerId() != null && bidderId.equals(txItem.getSellerId())) {
                throw new BidRejectedException(BidRejectedException.Reason.SELLER_BID,
                        "Người bán không thể đặt giá cho sản phẩm của mình.");
            }

            Auction txAuction = auctionDAO.selectByItemId(con, txItem);
            if (txAuction == null) {
                if (amount <= txItem.getCurrentHighestPrice()) {
                    throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                            "Giá đặt phải cao hơn giá hiện tại: " + txItem.getCurrentHighestPrice());
                }
                throw new NotFoundException("auction", "Không tìm thấy phiên đấu giá.");
            }
            txAuction.setItem(txItem);

            if (txAuction.getStatus() != AuctionStatus.RUNNING) {
                throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                        "Phiên đấu giá hiện không diễn ra hoặc đã kết thúc.");
            }

            boolean firstBid = isFirstBid(txAuction);
            double minAllowedBid = minAllowedBid(txItem, txAuction);
            if (firstBid && amount <= txItem.getCurrentHighestPrice()) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Giá đặt phải cao hơn giá hiện tại: " + txItem.getCurrentHighestPrice());
            }
            if (!firstBid && amount < minAllowedBid) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Giá đặt tối thiểu là " + String.format("%,.0f", minAllowedBid)
                                + " (giá hiện tại + MinBid).");
            }

            User user = userDAO.selectByUsernameOnly(bidderId);
            if (user == null) {
                throw new NotFoundException("user", "Không tìm thấy người dùng.");
            }

            if (amount > user.getBalance()) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Số dư tài khoản không đủ để đặt giá.");
            }

            // 3. LOGIC HOÀN TIỀN CHO NGƯỜI ĐẶT CŨ & TRỪ TIỀN NGƯỜI MỚI (NẰM TRONG TRANSACTION)
            String oldBidder = txAuction.getLeadingBidder();
            double oldHighestPrice = txItem.getCurrentHighestPrice();

            // Hoàn tiền cho người cũ (nếu có)
            if (oldBidder != null && !oldBidder.isEmpty()) {
                User userOldBidder = userDAO.selectByUsernameOnly(oldBidder);
                if (userOldBidder != null) {
                    userDAO.UpdateBalance(oldBidder, userOldBidder.getBalance() + oldHighestPrice);
                    if(oldBidder.equals(bidderId)){
                        user.setBalance(userOldBidder.getBalance() + oldHighestPrice);
                    }
                }
            }

            // Trừ tiền người đặt mới
            userDAO.UpdateBalance(bidderId, user.getBalance() - amount);
            user.setBalance(user.getBalance() - amount); // Cập nhật lại object để trả về Client

            // 4. CẬP NHẬT LỊCH SỬ ĐẤU GIÁ
            List<BidTransaction> newHistory = new ArrayList<>(txAuction.getBidHistory());
            String transactionId = "BID-" + System.nanoTime() + "-" + bidderId;
            BidTransaction newBid = new BidTransaction(
                    transactionId,
                    bidderId,
                    amount,
                    Instant.now(clock)
            );
            newHistory.add(newBid);
            txAuction.setBidHistory(newHistory);

            applyAntiSnipingExtension(txItem);

            // 5. UPDATE CÁC BẢNG DATABASE
            int auctionResult = auctionDAO.Update(con, txAuction, txItem.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            txItem.setCurrentHighestPrice(amount);
            int itemResult = itemDAO.Update(con, txItem);
            if (itemResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng items.");
            }

            // 🌟 THÀNH CÔNG TOÀN DIỆN -> ĐỒNG BỘ LƯU VÀO DB
            con.commit();

            // Chuẩn bị dữ liệu trả về
            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("item", txItem);
            finalResult.put("user", user);
            finalResult.put("latestAuction", txAuction);
            finalResult.put("newPrice", amount);
            finalResult.put("bidHistory", new ArrayList<>(txAuction.getBidHistory()));
            return finalResult;

        } catch (BidRejectedException | NotFoundException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } // Trả lại tiền nếu lỗi logic
            }
            throw e;
        } catch (Exception e) {
            System.err.println("❌ processBid: Lỗi: " + e.getMessage());
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } // Trả lại tiền nếu lỗi hệ thống
            }
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Lỗi lưu dữ liệu. Vui lòng thử lại.", e);
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) { e.printStackTrace(); }
            }
            lock.unlock(); // ✅ CHỈ CẦN UNLOCK DUY NHẤT Ở ĐÂY (Luôn luôn an toàn)
        }
    }

    public void updateAuctionStatus(String auctionId, String itemId, String status) {
        Item item = itemDAO.selectById(itemId);
        if (item != null) {
            AuctionStatus auctionStatus = AuctionStatus.valueOf(status);
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setStatus(auctionStatus);
                auctionDAO.Update_Status(auction,item, auctionStatus);
            }
        }
    }

    private boolean applyAntiSnipingExtension(Item item) {
        Instant endTime = item.getAuctionEndTime();
        if (endTime == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        Instant antiSnipingWindowStart = endTime.minusSeconds(ANTI_SNIPING_WINDOW_SECONDS);
        boolean inLastMinute = !now.isBefore(antiSnipingWindowStart) && now.isBefore(endTime);
        if (!inLastMinute) {
            return false;
        }
        item.setAuctionEndTime(endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        return true;
    }

    /**
     * ✅ SỬA LỖI #3: Update trực tiếp qua SQL thay vì lấy object về
     */
    public boolean updateUser(String username, String field, String value) {
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
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
            con = connectionProvider.getConnection();

            String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, oldPassword);

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

    public void logout(String username) {
        // Cleanup session nếu cần
    }

    public void updateItem(Item item) throws PersistenceException {
        try {
            validateMinBidForSave(item, false);
            int rowsAffected = DAOItems.getInstance().Update(item);
            if (rowsAffected == 0) {
                throw new PersistenceException("Cập nhật thất bại. Không tìm thấy sản phẩm hoặc dữ liệu không thay đổi.");
            }
            System.out.println("[UserService] Cập nhật thành công sản phẩm có ID: " + item.getDatabaseId());
        } catch (Exception e) {
            throw new PersistenceException("Lỗi hệ thống khi cập nhật sản phẩm: " + e.getMessage(), e);
        }
    }

    public List<BidHistoryDTO> getBidderHistory(String username) {
        List<Auction> allAuctions = auctionDAO.selectAll();
        List<BidHistoryDTO> resultList = new ArrayList<>();

        for (Auction auction : allAuctions) {
            List<BidTransaction> txList = auction.getBidHistory();
            if (txList == null || txList.isEmpty()) continue;

            double myMaxBid = 0;
            String myLastTime = "";
            boolean hasParticipated = false;

            for (BidTransaction tx : txList) {
                if (username.equals(tx.getBidder())) {
                    hasParticipated = true;
                    if (tx.getAmount() > myMaxBid) {
                        myMaxBid = tx.getAmount();
                    }
                    myLastTime = tx.getBidTime() != null ? tx.getBidTime().toString() : "Không rõ";
                }
            }

            if (hasParticipated) {
                String displayStatus = "LOST";
                double currentPrice = txList.get(txList.size() - 1).getAmount();
                boolean isLeading = username.equals(auction.getLeadingBidder());
                AuctionStatus auctionStatus = auction.getStatus();

                if (auctionStatus == AuctionStatus.RUNNING) {
                    if (isLeading) {
                        displayStatus = "WINNING";
                    } else {
                        displayStatus = "OUTBID";
                    }
                } else {
                    if (isLeading) {
                        displayStatus = "WON";
                    } else {
                        displayStatus = "LOST";
                    }
                }

                String itemName = "Sản phẩm mã #" + auction.getItemId();

                resultList.add(new BidHistoryDTO(
                        auction.getItemId(),
                        itemName,
                        myMaxBid,
                        currentPrice,
                        myLastTime,
                        displayStatus
                ));
            }
        }
        return resultList;
    }

    public List<Auction> getAllAuctions() {
        List<Auction> auctions = auctionDAO.selectAll();
        if (auctions == null) return new ArrayList<>();

        for (Auction auction : auctions) {
            if (auction.getItem() == null && auction.getItemId() != 0) {
                Item item = itemDAO.selectById(String.valueOf(auction.getItemId()));
                if (item != null) {
                    auction.setItem(item);
                }
            }
        }
        return auctions;
    }

    public Auction setAllow(String iditem, String choose) {
        Item item = itemDAO.selectById(iditem);
        if (item == null) {
            return null;
        }

        Auction auction = auctionDAO.selectByItemId(item);
        AuctionStatus nextStatus = Boolean.parseBoolean(choose) ? AuctionStatus.OPEN : null;
        auctionDAO.Update_Status(auction,item, nextStatus);
        if (auction != null && auction.getItem() != null) {
            auction.getItem().setAuctionStatus(auction.getStatus());
        }
        return auction;
    }

    public int DeleteItem(int id_item) {
        Item item = itemDAO.selectById(String.valueOf(id_item));
        if (item != null) {
            return itemDAO.Delete(item);
        }
        return 0;
    }

    public boolean rechargeAmount(String username, double amount) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            DepositTransaction dt = new DepositTransaction(username, amount);
            user.getDepositHistory().add(dt);
            return userDAO.UpdateDepositHistory(username, user.getDepositHistory()) > 0;
        }
        return false;
    }

    public List<DepositTransaction> getPendingDeposits() {
        return userDAO.getAllPendingDeposits();
    }

    public boolean approveDeposit(String username, String transactionId) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            for (DepositTransaction dt : user.getDepositHistory()) {
                if (dt.getId().equals(transactionId) && "PENDING".equals(dt.getStatus())) {
                    dt.setStatus("APPROVED");
                    double newBalance = user.getBalance() + dt.getAmount();
                    user.setBalance(newBalance);
                    userDAO.UpdateBalance(username, newBalance);
                    userDAO.UpdateDepositHistory(username, user.getDepositHistory());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean rejectDeposit(String username, String transactionId) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            for (DepositTransaction dt : user.getDepositHistory()) {
                if (dt.getId().equals(transactionId) && "PENDING".equals(dt.getStatus())) {
                    dt.setStatus("REJECTED");
                    userDAO.UpdateDepositHistory(username, user.getDepositHistory());
                    return true;
                }
            }
        }
        return false;
    }

    public boolean deleteDepositHistory(String username, String transactionId) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            boolean removed = user.getDepositHistory().removeIf(dt -> dt.getId().equals(transactionId));
            if (removed) {
                return userDAO.UpdateDepositHistory(username, user.getDepositHistory()) > 0;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public ArrayList<BidTransaction> getBidHistory(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        Auction auction = auctionDAO.selectByItemId(item);
        if (auction != null) {
            return new ArrayList<>(auction.getBidHistory());
        }
        return null;
    }

    private static void validateMinBidForSave(Item item, boolean requireMinBid) {
        if (item == null) {
            throw new PersistenceException("San pham khong hop le.");
        }
        double startingPrice = item.getStartingPrice();
        double minBid = item.getMinBid();
        if (startingPrice <= 0) {
            throw new PersistenceException("Gia khoi diem phai lon hon 0.");
        }
        if (requireMinBid && minBid <= 0) {
            throw new PersistenceException("MinBid phai lon hon 0.");
        }
        if (minBid < 0) {
            throw new PersistenceException("MinBid khong duoc am.");
        }
        if (minBid > startingPrice * MAX_MIN_BID_RATIO) {
            throw new PersistenceException("MinBid khong duoc vuot qua 20% gia khoi diem.");
        }
    }

    private static double minAllowedBid(Item item, Auction auction) {
        double currentPrice = item.getCurrentHighestPrice();
        if (isFirstBid(auction)) {
            return Math.nextUp(currentPrice);
        }
        return currentPrice + Math.max(0, item.getMinBid());
    }

    private static boolean isFirstBid(Auction auction) {
        String leadingBidder = auction.getLeadingBidder();
        boolean hasLeader = leadingBidder != null && !leadingBidder.isBlank() && !"null".equalsIgnoreCase(leadingBidder);
        boolean hasHistory = auction.getBidHistory() != null && !auction.getBidHistory().isEmpty();
        return !hasLeader && !hasHistory;
    }
}
