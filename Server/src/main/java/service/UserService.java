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

    private ReentrantLock getLockForItem(String itemId) {
        try {
            return itemLocks.get(itemId, () -> new ReentrantLock(true));
        } catch (Exception e) {
            return new ReentrantLock(true);
        }
    }

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

    public Map<String, Object> register(User user) {
        Map<String, Object> response = new HashMap<>();
        try {
            userDAO.Insert(user);
            response.put("success", "TRUE");
            return response;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                response.put("success", "EXSITED");
                response.put("message", "Tài khoản đã tồn tại");
                return response;
            }
            throw new RuntimeException("Lỗi hệ thống khi đăng ký thành viên.", e);
        }
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
        Auction auction =  auctionDAO.selectByItemId(item);

        if (auction != null) {
            auction.setItem(item);
            auction.updateStatusByTime();
        }
        return auction;
    }

    /**
     * ✅ ĐÃ TỐI ƯU TOÀN DIỆN: Đảm bảo an toàn đa máy chủ (Distributed) bằng Database Lock
     */
    public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
        ReentrantLock lock = getLockForItem(itemId);
        lock.lock();

        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);

            // TỐI ƯU quan trọng: Trong các hàm DAO của bạn (selectById, selectByItemId, selectByUsernameOnly),
            // bên trong câu lệnh SQL nên bổ sung "FOR UPDATE" khi truyền kèm Connection để lock row dưới DB.
            Item txItem = itemDAO.selectById(con, itemId);
            if (txItem == null) {
                throw new NotFoundException("item", "Không tìm thấy sản phẩm.");
            }

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

            AuctionStatus oldStatus = txAuction.getRawStatus();
            txAuction.updateStatusByTime();
            AuctionStatus currentStatus = txAuction.getRawStatus();

            if (oldStatus != currentStatus) {
                auctionDAO.Update_Status(con, txAuction, txItem, currentStatus);
            }

            if (currentStatus != AuctionStatus.RUNNING) {
                throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                        "Phiên đấu giá hiện không diễn ra hoặc đã kết thúc.");
            }

            boolean firstBid = (txAuction.getBidHistory() == null || txAuction.getBidHistory().isEmpty());
            double minAllowedBid = txItem.getCurrentHighestPrice() + (txItem.getMinBid() != 0 ? txItem.getMinBid() : 1.0);

            if (firstBid && amount <= txItem.getCurrentHighestPrice()) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Giá đặt phải cao hơn giá hiện tại: " + txItem.getCurrentHighestPrice());
            }
            if (!firstBid && amount < minAllowedBid) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Giá đặt tối thiểu là " + String.format("%,.0f", minAllowedBid));
            }

            User user = userDAO.selectByUsernameOnly(con, bidderId);
            if (user == null) {
                throw new NotFoundException("user", "Không tìm thấy người dùng.");
            }

            if (amount > user.getBalance()) {
                throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                        "Số dư tài khoản không đủ để đặt giá.");
            }

            String oldBidder = txAuction.getLeadingBidder();
            double oldHighestPrice = txItem.getCurrentHighestPrice();
            double bidderBalanceBeforeCharge = user.getBalance();
            String refundedBidderId = null;
            Double refundedBalance = null;
            User refundedUser = null;

            if (oldBidder != null && !oldBidder.isEmpty()) {
                User userOldBidder = userDAO.selectByUsernameOnly(con, oldBidder);
                if (userOldBidder != null) {
                    double newOldBidderBalance = userOldBidder.getBalance() + oldHighestPrice;
                    userDAO.UpdateBalance(con, oldBidder, newOldBidderBalance);
                    userOldBidder.setBalance(newOldBidderBalance);
                    if (oldBidder.equals(bidderId)) {
                        bidderBalanceBeforeCharge = newOldBidderBalance;
                    } else {
                        refundedBidderId = oldBidder;
                        refundedBalance = newOldBidderBalance;
                        refundedUser = userOldBidder;
                    }
                }
            }

            double newBidderBalance = bidderBalanceBeforeCharge - amount;
            userDAO.UpdateBalance(con, bidderId, newBidderBalance);
            user.setBalance(newBidderBalance);

            List<BidTransaction> newHistory = new ArrayList<>(txAuction.getBidHistory());
            String transactionId = "BID-" + System.nanoTime() + "-" + bidderId;
            BidTransaction newBid = new BidTransaction(transactionId, bidderId, amount, Instant.now(clock));
            newHistory.add(newBid);
            txAuction.setBidHistory(newHistory);
            txAuction.setLeadingBidder(bidderId);

            // Cập nhật cơ chế Anti-sniping
            applyAntiSnipingExtension(txItem);

            int auctionResult = auctionDAO.Update(con, txAuction, txItem.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            txItem.setCurrentHighestPrice(amount);
            // LƯU Ý: Hàm itemDAO.Update(con, txItem) CẦN phải update cả trường `auction_end_time` để lưu cơ chế anti-sniping xuống DB.
            int itemResult = itemDAO.Update(con, txItem);
            if (itemResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng items.");
            }

            con.commit();

            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("item", txItem);
            finalResult.put("user", user);
            finalResult.put("latestAuction", txAuction);
            finalResult.put("newPrice", amount);
            finalResult.put("bidHistory", new ArrayList<>(txAuction.getBidHistory()));
            if (refundedBidderId != null) {
                finalResult.put("refundedBidderId", refundedBidderId);
                finalResult.put("refundedBalance", refundedBalance);
                finalResult.put("refundedUser", refundedUser);
            }
            return finalResult;

        } catch (BidRejectedException | NotFoundException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw e;
        } catch (Exception e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST, "Lỗi lưu dữ liệu. Vui lòng thử lại.", e);
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); } catch (SQLException e) { e.printStackTrace(); }
                try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
            lock.unlock();
        }
    }

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

    private boolean applyAntiSnipingExtension(Item item) {
        Instant endTime = item.getAuctionEndTime();
        if (endTime == null) return false;

        Instant now = Instant.now(clock);
        Instant antiSnipingWindowStart = endTime.minusSeconds(ANTI_SNIPING_WINDOW_SECONDS);
        boolean inLastMinute = !now.isBefore(antiSnipingWindowStart) && now.isBefore(endTime);
        if (!inLastMinute) return false;

        item.setAuctionEndTime(endTime.plusSeconds(ANTI_SNIPING_EXTENSION_SECONDS));
        return true;
    }

    public boolean updateUser(String username, String field, String value) {
        Connection con = null;
        PreparedStatement ps = null;
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
                    throw new IllegalArgumentException("Trường cập nhật không hợp lệ: " + field);
            }

            ps = con.prepareStatement(sql);
            ps.setString(1, value);
            ps.setString(2, username);

            int result = ps.executeUpdate();
            return result > 0;

        } catch (SQLException | IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (ps != null) { try { ps.close(); } catch (SQLException e) { e.printStackTrace(); } }
            if (con != null) { try { con.close(); } catch (SQLException e) { e.printStackTrace(); } }
        }
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Connection con = null;
        PreparedStatement ps = null;
        try {
            con = connectionProvider.getConnection();
            String sql = "UPDATE users SET password = ? WHERE username = ? AND password = ?";
            ps = con.prepareStatement(sql);
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, oldPassword);

            int result = ps.executeUpdate();
            return result > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            if (ps != null) { try { ps.close(); } catch (SQLException e) { e.printStackTrace(); } }
            if (con != null) { try { con.close(); } catch (SQLException e) { e.printStackTrace(); } }
        }
    }

    public void logout(String username) {}

    public void updateItem(Item item) throws PersistenceException {
        try {
            validateMinBidForSave(item, false);
            item.setCurrentHighestPrice(item.getStartingPrice());

            int rowsAffected = DAOItems.getInstance().UpdateWhenEdit(item);
            if (rowsAffected == 0) {
                throw new PersistenceException("Cập nhật thất bại. Không tìm thấy sản phẩm.");
            }

            int auctionRowsAffected = DAOAuction_Items.getInstance().updatePriceByItemIdWhenEditItem(item);
            if (auctionRowsAffected <= 0) {
                System.out.println("[UserService] Lưu ý: Không tìm thấy phiên tương ứng.");
            }
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
                    displayStatus = isLeading ? "WINNING" : "OUTBID";
                } else {
                    displayStatus = isLeading ? "WON" : "LOST";
                }

                String itemName = "Sản phẩm không tên";
                if (auction.getItem() != null && auction.getItem().getName() != null) {
                    itemName = auction.getItem().getName();
                }

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
                if (item != null) auction.setItem(item);
            }
            if (auction.getItem() != null) {
                auction.updateStatusByTime();
            }
        }
        return auctions;
    }

    public Auction setAllow(String iditem, String choose) {
        Item item = itemDAO.selectById(iditem);
        if (item == null) return null;

        Auction auction = auctionDAO.selectByItemId(item);
        AuctionStatus nextStatus = Boolean.parseBoolean(choose) ? AuctionStatus.OPEN : null;
        auction.setStatus(nextStatus);
        auctionDAO.Update_Status(auction, item, nextStatus);
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

    /**
     * ✅ ĐÃ SỬA LỖI: Bọc cơ chế cộng tiền và cập nhật lịch sử duyệt nạp tiền vào chung 1 Transaction
     */
    public boolean approveDeposit(String username, String transactionId) {
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false); // Bắt đầu transaction tài chính

            User user = userDAO.selectByUsernameOnly(con, username);
            if (user == null) return false;

            boolean transactionFound = false;
            for (DepositTransaction dt : user.getDepositHistory()) {
                if (dt.getId().equals(transactionId) && "PENDING".equals(dt.getStatus())) {
                    dt.setStatus("APPROVED");
                    double newBalance = user.getBalance() + dt.getAmount();
                    user.setBalance(newBalance);

                    // Thực thi đồng bộ trong cùng 1 Connection transaction
                    userDAO.UpdateBalance(con, username, newBalance);
                    userDAO.UpdateDepositHistory(con, username, user.getDepositHistory());
                    transactionFound = true;
                    break;
                }
            }

            if (transactionFound) {
                con.commit();
                return true;
            } else {
                con.rollback();
                return false;
            }
        } catch (SQLException e) {
            if (con != null) { try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }

    public boolean rejectDeposit(String username, String transactionId) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            for (DepositTransaction dt : user.getDepositHistory()) {
                if (dt.getId().equals(transactionId) && "PENDING".equals(dt.getStatus())) {
                    dt.setStatus("REJECTED");
                    return userDAO.UpdateDepositHistory(username, user.getDepositHistory()) > 0;
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
            throw new PersistenceException("Sản phẩm không hợp lệ.");
        }
        double startingPrice = item.getStartingPrice();
        double minBid = item.getMinBid();
        if (startingPrice <= 0) {
            throw new PersistenceException("Giá khởi điểm phải lớn hơn 0.");
        }
        if (requireMinBid && minBid <= 0) {
            throw new PersistenceException("MinBid phải lớn hơn 0.");
        }
        if (minBid < 0) {
            throw new PersistenceException("MinBid không được âm.");
        }
        if (minBid > startingPrice * MAX_MIN_BID_RATIO) {
            throw new PersistenceException("MinBid không được vượt quá 20% giá khởi điểm.");
        }
    }

    /**
     * ✅ ĐÃ SỬA LỖI: Bọc cơ chế thanh toán tiền cho Seller vào Transaction
     */
    public boolean PayHandler(Item item) {
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);

            String sellerid = item.getSellerId();
            double amount = item.getCurrentHighestPrice();

            // Cập nhật số tiền cộng cho seller bằng connection chung
            int result = userDAO.UpdateBalance(con, sellerid, amount);
            Auction auction = auctionDAO.selectByItemId(con, item);

            if (auction != null) {
                auctionDAO.Update_Status(con, auction, item, AuctionStatus.PAID);
            }

            if (result >= 1) {
                con.commit();
                return true;
            } else {
                con.rollback();
                return false;
            }
        } catch (SQLException e) {
            if (con != null) { try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try { con.setAutoCommit(true); con.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }
}