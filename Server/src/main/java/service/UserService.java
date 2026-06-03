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
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@FunctionalInterface
interface ConnectionProvider {
    Connection getConnection() throws SQLException;
}

// Nghiệp vụ user, bid và thanh toán.
public class UserService {
    static final long ANTI_SNIPING_WINDOW_SECONDS = 60;
    static final long ANTI_SNIPING_EXTENSION_SECONDS = 90;
    private static final double MAX_MIN_BID_RATIO = 0.20;

    private static final Cache<String, ReentrantLock> itemLocks = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES)
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
    // Lấy khóa theo item.
    private ReentrantLock getLockForItem(String itemId) {
        try {
            return itemLocks.get(itemId,ReentrantLock::new);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Không thể khởi tạo hoặc lấy Lock từ Cache cho item: " + itemId, e.getCause());
        }
    }
    // Đăng nhập.
    public User loginAndGetUser(String username, String password) {
        User user = userDAO.selectByUsername(username, password);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        throw new UnauthorizedException("Sai tên đăng nhập hoặc mật khẩu.");
    }
    // Lấy user theo username.
    public User getUserOnly(String username) {
        return userDAO.selectByUsernameOnly(username);
    }
    // Đăng ký.
    public Map<String, Object> register(User user) {

        if (userDAO.selectByUsernameOnly(user.getUsername()) != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", "EXSITED");
            response.put("message", "Tài khoản đã tồn tại");
            return response;
        }

        try {
            userDAO.Insert(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", "TRUE");
            return response;
        } catch (SQLException e) {

            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            String sqlState = e.getSQLState();
            if ((msg.contains("unique") || msg.contains("duplicate") || (sqlState != null && sqlState.startsWith("23")))) {

                if (userDAO.selectByUsernameOnly(user.getUsername()) != null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", "EXSITED");
                    response.put("message", "Tài khoản đã tồn tại");
                    return response;
                }
            }
            throw new RuntimeException(e);
        }
    }
    // Tạo sản phẩm.
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
    // Lấy danh sách sản phẩm.
    public ArrayList<Item> select_items(UserRole role) {
        ArrayList<Item> list = itemDAO.selectAll();
        if (role == UserRole.ADMIN) {
            return list;
        }
        list.removeIf(item -> item.getAuctionStatus() == null);
        return list;
    }
    // Lấy phiên theo item.
    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        return auctionDAO.selectByItemId(item);
    }
    // Ghi nhận user vào phòng.
    public void SetAuctionByItemId(String itemId, String userid) {

    }
    // Đặt bid.
    public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);
        } catch (SQLException e) {
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST, "Lỗi kết nối hệ thống.", e);
        }

        // Khóa item tránh bid đồng thời.
        ReentrantLock lock = getLockForItem(itemId);
        lock.lock();

        try {
            // Đọc dữ liệu mới nhất.
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
                throw new NotFoundException("auction", "Không tìm thấy phiên đấu giá.");
            }
            txAuction.setItem(txItem);

            if (txAuction.getStatus() != AuctionStatus.RUNNING) {
                throw new BidRejectedException(BidRejectedException.Reason.NOT_RUNNING,
                        "Phiên đấu giá hiện không diễn ra hoặc đã kết thúc.");
            }

            // Kiểm tra giá đặt.
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

            User currentBidder = userDAO.selectByUsernameOnly(bidderId);
            if (currentBidder == null) {
                throw new NotFoundException("user", "Không tìm thấy người dùng.");
            }

            User refundedUser = null;
            String refundedBidderId = null;
            Double refundedBalance = null;
            double currentBalance = currentBidder.getBalance();
            double currentBidderNewBalance;

            // Hoàn tiền bid cũ, trừ tiền bid mới.
            String oldBidderId = txAuction.getLeadingBidder();
            double oldHighestPrice = txItem.getCurrentHighestPrice();

            if (oldBidderId != null && !oldBidderId.isEmpty()) {
                if (oldBidderId.equals(bidderId)) {
                    double delta = amount - oldHighestPrice;
                    currentBidderNewBalance = currentBalance - delta;
                    if (currentBidderNewBalance < 0) {
                        throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                                "Số dư tài khoản không đủ để nâng giá.");
                    }

                    int rows = userDAO.UpdateBalanceWithCondition(con, bidderId, currentBidderNewBalance, delta);
                    if (rows <= 0) {
                        throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                                "Số dư thay đổi bất thường, vui lòng thử lại.");
                    }
                } else {
                    if (currentBalance < amount) {
                        throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                                "Số dư tài khoản không đủ để đặt giá.");
                    }

                    if (!oldBidderId.equalsIgnoreCase("null")) {
                        refundedUser = userDAO.selectByUsernameOnly(oldBidderId);
                        if (refundedUser != null) {
                            refundedBidderId = oldBidderId;
                            refundedBalance = refundedUser.getBalance() + oldHighestPrice;
                            userDAO.UpdateBalance(con, oldBidderId, refundedBalance);
                        }
                    }

                    currentBidderNewBalance = currentBalance - amount;
                    int rows = userDAO.UpdateBalanceWithCondition(con, bidderId, currentBidderNewBalance, amount);
                    if (rows <= 0) {
                        throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                                "Đặt giá thất bại do số dư không khớp.");
                    }
                }
            } else {
                if (currentBalance < amount) {
                    throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                            "Số dư tài khoản không đủ để đặt giá.");
                }
                currentBidderNewBalance = currentBalance - amount;
                int rows = userDAO.UpdateBalanceWithCondition(con, bidderId, currentBidderNewBalance, amount);
                if (rows <= 0) {
                    throw new BidRejectedException(BidRejectedException.Reason.PRICE_TOO_LOW,
                            "Đặt giá thất bại.");
                }
            }

            // Ghi lịch sử bid.
            List<BidTransaction> newHistory = new ArrayList<>(txAuction.getBidHistory());
            String transactionId = "BID-" + UUID.randomUUID().toString().substring(0, 8) + "-" + bidderId;
            BidTransaction newBid = new BidTransaction(
                    transactionId,
                    bidderId,
                    amount,
                    Instant.now(clock)
            );
            newHistory.add(newBid);
            txAuction.setBidHistory(newHistory);
            txAuction.setLeadingBidder(bidderId);

            txItem.setCurrentHighestPrice(amount);
            applyAntiSnipingExtension(txItem);

            int auctionResult = auctionDAO.Update(con, txAuction, txItem.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            int itemResult = itemDAO.Update(con, txItem);
            if (itemResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng items.");
            }

            // Chốt transaction.
            con.commit();

            currentBidder.setBalance(currentBidderNewBalance);
            if (refundedUser != null && refundedBalance != null) {
                refundedUser.setBalance(refundedBalance);
            }

            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("item", txItem);
            finalResult.put("user", currentBidder);
            finalResult.put("latestAuction", txAuction);
            finalResult.put("newPrice", amount);
            finalResult.put("bidHistory", newHistory);

            if (refundedBidderId != null) {
                finalResult.put("refundedBidderId", refundedBidderId);
                finalResult.put("refundedBalance", refundedBalance);
                finalResult.put("refundedUser", refundedUser);
            }
            return finalResult;

        } catch (BidRejectedException | NotFoundException e) {
            rollbackConnection(con);
            throw e;
        } catch (Exception e) {
            rollbackConnection(con);
            throw new BidRejectedException(BidRejectedException.Reason.PERSIST,
                    "Lỗi lưu dữ liệu. Vui lòng thử lại.", e);
        } finally {
            closeConnection(con);
            lock.unlock();
        }
    }

    // Rollback transaction.
    private void rollbackConnection(Connection con) {
        if (con != null) {
            try { con.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        }
    }

    // Đóng connection.
    private void closeConnection(Connection con) {
        if (con != null) {
            try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
        }
    }
    // Cập nhật trạng thái phiên.
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
    // Gia hạn sát giờ.
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
    // Cập nhật user.
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
    // Đổi mật khẩu.
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
    // Đăng xuất.
    public void logout(String username) {

    }
    // Cập nhật sản phẩm.
    public void updateItem(Item item) throws PersistenceException {
        try {
            validateMinBidForSave(item, false);

            item.setCurrentHighestPrice(item.getStartingPrice());

            int rowsAffected = DAOItems.getInstance().UpdateWhenEdit(item);
            if (rowsAffected == 0) {
                throw new PersistenceException("Cập nhật thất bại. Không tìm thấy sản phẩm hoặc dữ liệu không thay đổi.");
            }
            System.out.println("[UserService] Cập nhật thành công sản phẩm có ID: " + item.getDatabaseId());

            int auctionRowsAffected = DAOAuction_Items.getInstance().updatePriceByItemIdWhenEditItem(item);
            if (auctionRowsAffected > 0) {
                System.out.println("[UserService] Phiên chưa diễn ra. Đã đồng bộ giá hiện tại (currentPrice) thành: " + item.getCurrentHighestPrice());
            } else {
                System.out.println("[UserService] Lưu ý: Sản phẩm được cập nhật nhưng không tìm thấy phiên đấu giá tương ứng.");
            }

        } catch (Exception e) {
            throw new PersistenceException("Lỗi hệ thống khi cập nhật sản phẩm: " + e.getMessage(), e);
        }
    }
    // Lấy lịch sử bidder.
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

                String itemName = "mmb";
                double minBidForDto = 0.0;

                if (auction.getItem() != null) {
                    if (auction.getItem().getName() != null) {
                        itemName = auction.getItem().getName();
                    }
                    minBidForDto = auction.getItem().getMinBid();
                }

                String sellerIdForDto = null;
                if (auction.getItem() != null && auction.getItem().getSellerId() != null) {
                    sellerIdForDto = auction.getItem().getSellerId();
                } else if (auction.getSellerID() != null) {
                    sellerIdForDto = auction.getSellerID();
                }

                resultList.add(new BidHistoryDTO(
                        auction.getItemId(),
                        itemName,
                        sellerIdForDto,
                        myMaxBid,
                        currentPrice,
                        minBidForDto,
                        myLastTime,
                        displayStatus
                ));
            }
        }
        return resultList;
    }
    // Lấy toàn bộ phiên.
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
    // Duyệt hoặc dừng phiên.
    public Auction setAllow(String iditem, String choose) {
        Item item = itemDAO.selectById(iditem);
        if (item == null) {
            return null;
        }

        Auction auction = auctionDAO.selectByItemId(item);
        AuctionStatus nextStatus = Boolean.parseBoolean(choose) ? AuctionStatus.OPEN : null;
        auction.setStatus(nextStatus);
        auctionDAO.Update_Status(auction,item, nextStatus);
        return auction;
    }
    // Xóa sản phẩm.
    public int DeleteItem(int id_item) {
        Item item = itemDAO.selectById(String.valueOf(id_item));
        if (item != null) {
            return itemDAO.Delete(item);
        }
        return 0;
    }
    // Tạo yêu cầu nạp tiền.
    public boolean rechargeAmount(String username, double amount) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user != null) {
            DepositTransaction dt = new DepositTransaction(username, amount);
            user.getDepositHistory().add(dt);
            return userDAO.UpdateDepositHistory(username, user.getDepositHistory()) > 0;
        }
        return false;
    }
    // Lấy nạp tiền chờ duyệt.
    public List<DepositTransaction> getPendingDeposits() {
        return userDAO.getAllPendingDeposits();
    }
    // Duyệt nạp tiền.
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
    // Từ chối nạp tiền.
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
    // Xóa lịch sử nạp tiền.
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

    // Lấy lịch sử bid.
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
    // Kiểm tra minBid.
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
    // Tính giá bid tối thiểu.
    private static double minAllowedBid(Item item, Auction auction) {
        double currentPrice = item.getCurrentHighestPrice();
        if (isFirstBid(auction)) {
            return Math.nextUp(currentPrice);
        }
        return currentPrice + Math.max(0, item.getMinBid());
    }
    // Kiểm tra bid đầu tiên.
    private static boolean isFirstBid(Auction auction) {
        String leadingBidder = auction.getLeadingBidder();
        boolean hasLeader = leadingBidder != null && !leadingBidder.isBlank() && !"null".equalsIgnoreCase(leadingBidder);
        boolean hasHistory = auction.getBidHistory() != null && !auction.getBidHistory().isEmpty();
        return !hasLeader && !hasHistory;
    }
    // Thanh toán phiên thắng.
    public boolean PayHandler(Item item) {
        if (item == null) {
            System.err.println("[UserService] PayHandler: item is null");
            return false;
        }

        String sellerid = item.getSellerId();
        if (sellerid == null || sellerid.isBlank()) {
            System.err.println("[UserService] PayHandler: sellerId is null or empty for item: " + item.getDatabaseId());
            return false;
        }

        User user = userDAO.selectByUsernameOnly(sellerid);
        if (user == null) {

            System.err.println("[UserService] PayHandler: user not found for sellerId: " + sellerid);
            return false;
        }

        double amount = item.getCurrentHighestPrice();
        double newBalance = user.getBalance() + amount;
        int result = userDAO.UpdateBalance(sellerid, newBalance);

        Auction auction = auctionDAO.selectByItemId(item);
        if (auction != null) {
            auctionDAO.Update_Status(auction, item, AuctionStatus.PAID);
        } else {
            System.err.println("[UserService] PayHandler: auction not found for item: " + item.getDatabaseId());
        }

        return result >= 1;
    }

}
