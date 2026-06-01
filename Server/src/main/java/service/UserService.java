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
import model.exception.BidRejectedException;
import model.exception.NotFoundException;
import model.exception.PersistenceException;
import model.exception.UnauthorizedException;

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
        if (userDAO.selectByUsernameOnly(user.getUsername()) != null) {
            response.put("success", "EXSITED");
            response.put("message", "Tài khoản đã tồn tại");
            return response;
        }

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
        for (Item item : list) {
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setItem(item);
                syncAuctionStatusWithDatabase(auction);
            }
        }

        if (role == UserRole.ADMIN) {
            return list;
        }
        list.removeIf(item -> item.getAuctionStatus() == null);
        return list;
    }

    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) {
            return null;
        }

        Auction auction = auctionDAO.selectByItemId(item);
        syncAuctionStatusWithDatabase(auction);
        return auction;
    }

    public void SetAuctionByItemId(String itemId, String userid) {
        // Tracking only.
    }

    public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
        ReentrantLock lock = getLockForItem(itemId);
        lock.lock();

        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);

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

            AuctionStatus currentStatus = effectiveAuctionStatus(txAuction, Instant.now(clock));
            txAuction.setStatus(currentStatus);
            txItem.setAuctionStatus(currentStatus);
            if (currentStatus != AuctionStatus.RUNNING) {
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

            applyAntiSnipingExtension(txItem);
            txAuction.setStatus(AuctionStatus.RUNNING);
            txItem.setAuctionStatus(AuctionStatus.RUNNING);

            int auctionResult = auctionDAO.Update(con, txAuction, txItem.getDatabaseId(), bidderId, amount);
            if (auctionResult <= 0) {
                throw new SQLException("Không thể cập nhật bảng auction_items.");
            }

            txItem.setCurrentHighestPrice(amount);
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
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw e;
        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
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
            lock.unlock();
        }
    }

    public void updateAuctionStatus(String auctionId, String itemId, String status) {
        Item item = itemDAO.selectById(itemId);
        if (item == null || status == null) {
            return;
        }

        String cleanStatus = status.replace("\"", "").trim().toUpperCase();
        try {
            AuctionStatus auctionStatus = AuctionStatus.valueOf(cleanStatus);
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setStatus(auctionStatus);
                item.setAuctionStatus(auctionStatus);
                auctionDAO.Update_Status(auction, item, auctionStatus);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Trạng thái không hợp lệ gửi tới updateAuctionStatus: " + status);
        }
    }

    private AuctionStatus syncAuctionStatusWithDatabase(Auction auction) {
        if (auction == null) {
            return null;
        }

        AuctionStatus storedStatus = auction.getRawStatus();
        AuctionStatus effectiveStatus = effectiveAuctionStatus(auction, Instant.now(clock), storedStatus);
        if (effectiveStatus != storedStatus) {
            auction.setStatus(effectiveStatus);
            if (auction.getItem() != null) {
                auctionDAO.Update_Status(auction, auction.getItem(), effectiveStatus);
            }
        } else if (auction.getItem() != null) {
            auction.getItem().setAuctionStatus(effectiveStatus);
        }
        return effectiveStatus;
    }

    private AuctionStatus effectiveAuctionStatus(Auction auction, Instant now) {
        return effectiveAuctionStatus(auction, now, auction == null ? null : auction.getRawStatus());
    }

    private AuctionStatus effectiveAuctionStatus(Auction auction, Instant now, AuctionStatus storedStatus) {
        if (auction == null || storedStatus == null
                || storedStatus == AuctionStatus.CANCELLED
                || storedStatus == AuctionStatus.PAID) {
            return storedStatus;
        }

        Item item = auction.getItem();
        if (item == null || item.getAuctionStartTime() == null || item.getAuctionEndTime() == null) {
            return storedStatus;
        }

        if (!now.isBefore(item.getAuctionEndTime())) {
            return AuctionStatus.FINISHED;
        }
        if (!now.isBefore(item.getAuctionStartTime())) {
            return AuctionStatus.RUNNING;
        }
        return AuctionStatus.OPEN;
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

    public List<Auction> getAllAuctions() {
        List<Auction> auctions = auctionDAO.selectAll();
        if (auctions == null) {
            return new ArrayList<>();
        }

        for (Auction auction : auctions) {
            if (auction.getItem() == null && auction.getItemId() != 0) {
                Item item = itemDAO.selectById(String.valueOf(auction.getItemId()));
                if (item != null) {
                    auction.setItem(item);
                }
            }
            syncAuctionStatusWithDatabase(auction);
        }
        return auctions;
    }

    public Auction setAllow(String iditem, String choose) {
        Item item = itemDAO.selectById(iditem);
        if (item == null) {
            return null;
        }

        Auction auction = auctionDAO.selectByItemId(item);
        if (auction == null) {
            return null;
        }

        AuctionStatus nextStatus = Boolean.parseBoolean(choose) ? AuctionStatus.OPEN : null;
        auction.setStatus(nextStatus);
        item.setAuctionStatus(nextStatus);
        auctionDAO.Update_Status(auction, item, nextStatus);
        return auction;
    }

    public boolean PayHandler(Item item) {
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);

            String sellerid = item.getSellerId();
            double amount = item.getCurrentHighestPrice();

            int result = userDAO.UpdateBalance(con, sellerid, amount);
            Auction auction = auctionDAO.selectByItemId(con, item);

            if (auction != null) {
                auction.setStatus(AuctionStatus.PAID);
                auctionDAO.Update_Status(con, auction, item, AuctionStatus.PAID);
            } else {
                item.setAuctionStatus(AuctionStatus.PAID);
            }

            if (result >= 1) {
                con.commit();
                return true;
            }

            con.rollback();
            return false;
        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public boolean updateUser(String username, String field, String value) {
        try (Connection con = connectionProvider.getConnection()) {
            String sql = switch (field) {
                case "name" -> "UPDATE users SET name = ? WHERE username = ?";
                case "phone" -> "UPDATE users SET phone = ? WHERE username = ?";
                case "address" -> "UPDATE users SET address = ? WHERE username = ?";
                default -> throw new IllegalArgumentException("Trường cập nhật không hợp lệ: " + field);
            };

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, value);
                ps.setString(2, username);
                return ps.executeUpdate() > 0;
            }
        } catch (IllegalArgumentException e) {
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        try (Connection con = connectionProvider.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET password = ? WHERE username = ? AND password = ?")) {
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, oldPassword);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public void logout(String username) {
    }

    public void updateItem(Item item) throws PersistenceException {
        try {
            validateMinBidForSave(item, false);
            item.setCurrentHighestPrice(item.getStartingPrice());

            int rowsAffected = itemDAO.UpdateWhenEdit(item);
            if (rowsAffected == 0) {
                throw new PersistenceException("Cập nhật thất bại. Không tìm thấy sản phẩm.");
            }

            int auctionRowsAffected = auctionDAO.updatePriceByItemIdWhenEditItem(item);
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
            if (txList == null || txList.isEmpty()) {
                continue;
            }

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

            if (!hasParticipated) {
                continue;
            }

            double currentPrice = txList.get(txList.size() - 1).getAmount();
            boolean isLeading = username.equals(auction.getLeadingBidder());
            AuctionStatus auctionStatus = syncAuctionStatusWithDatabase(auction);
            String displayStatus;
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
        return resultList;
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
        Connection con = null;
        try {
            con = connectionProvider.getConnection();
            con.setAutoCommit(false);

            User user = userDAO.selectByUsernameOnly(con, username);
            if (user == null) {
                con.rollback();
                return false;
            }

            boolean transactionFound = false;
            for (DepositTransaction dt : user.getDepositHistory()) {
                if (dt.getId().equals(transactionId) && "PENDING".equals(dt.getStatus())) {
                    dt.setStatus("APPROVED");
                    double newBalance = user.getBalance() + dt.getAmount();
                    user.setBalance(newBalance);
                    userDAO.UpdateBalance(con, username, newBalance);
                    userDAO.UpdateDepositHistory(con, username, user.getDepositHistory());
                    transactionFound = true;
                    break;
                }
            }

            if (transactionFound) {
                con.commit();
                return true;
            }

            con.rollback();
            return false;
        } catch (SQLException e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
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
        if (item == null) {
            return null;
        }

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

    private static double minAllowedBid(Item item, Auction auction) {
        double currentPrice = item.getCurrentHighestPrice();
        if (isFirstBid(auction)) {
            return Math.nextUp(currentPrice);
        }
        return currentPrice + Math.max(0, item.getMinBid());
    }

    private static boolean isFirstBid(Auction auction) {
        String leadingBidder = auction.getLeadingBidder();
        boolean hasLeader = leadingBidder != null
                && !leadingBidder.isBlank()
                && !"null".equalsIgnoreCase(leadingBidder);
        boolean hasHistory = auction.getBidHistory() != null && !auction.getBidHistory().isEmpty();
        return !hasLeader && !hasHistory;
    }
}
