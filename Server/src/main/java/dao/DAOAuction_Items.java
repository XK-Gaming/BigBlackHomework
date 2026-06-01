package dao;

import com.google.gson.Gson;
import database.JDBCUtil;
import model.Items.Item;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DAOAuction_Items {
    private static final DAOAuction_Items INSTANCE = new DAOAuction_Items();

    public static DAOAuction_Items getInstance() {
        return INSTANCE;
    }

    private final Gson gson = GsonUtils.createGson();

    public int Insert(Auction auction, Item item) {
        String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingbider, bidHistory, currentPrice) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setLong(1, item.getDatabaseId());
            pstmt.setString(2, item.getSellerId());

            String rawStatus = auction.getStatus() != null ? auction.getStatus().name() : AuctionStatus.OPEN.name();
            pstmt.setString(3, gson.toJson(rawStatus));

            String leadingUsername = auction.getLeadingBidder();
            pstmt.setString(4, leadingUsername);
            pstmt.setString(5, gson.toJson(new ArrayList<BidTransaction>()));

            pstmt.setDouble(6, item.getCurrentHighestPrice());

            return pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public int Update(Connection con, Auction auction, int itemId, String bidderId, Double price) throws SQLException {
        String sql = "UPDATE auction_items SET currentPrice = ?, leadingbider = ?, bidHistory = ? WHERE id_item = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, price);
            pstmt.setString(2, bidderId);
            pstmt.setString(3, gson.toJson(auction.getBidHistory()));
            pstmt.setLong(4, itemId);
            return pstmt.executeUpdate();
        }
    }

    public Auction selectByItemId(Item item) {
        try (Connection con = JDBCUtil.getConnection()) {
            return selectByItemId(con, item);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Auction selectByItemId(Connection con, Item item) throws SQLException {
        String sql = "SELECT * FROM auction_items WHERE id_item = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, item.getDatabaseId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToAuction(rs, item);
                }
            }
        }
        return null;
    }

    public void Update_Status(Connection con, Auction auction, Item item1, AuctionStatus status) throws SQLException {
        String sql = "UPDATE auction_items SET status = ? WHERE id_item = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, gson.toJson(status.name()));
            pstmt.setLong(2, item1.getDatabaseId());

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                auction.setStatus(status);
            }
        }
    }

    public void Update_Status(Auction auction, Item item1, AuctionStatus status) {
        try (Connection con = JDBCUtil.getConnection()) {
            Update_Status(con, auction, item1, status);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int Insert(Item item) {
        String sql = "INSERT INTO auction_items (id_item, sellerID, currentPrice, status) VALUES (?, ?, ?, ?)";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setLong(1, item.getDatabaseId());
            pstmt.setString(2, item.getSellerId());
            pstmt.setDouble(3, item.getCurrentHighestPrice());
            pstmt.setString(4, gson.toJson(AuctionStatus.OPEN.name()));

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi tại Insert: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    public List<Auction> selectAll() {
        List<Auction> list = new ArrayList<>();
        String sql = "SELECT " +
                "    a.id_item, a.status, a.currentPrice, a.leadingbider, a.bidHistory, " +
                "    i.name AS item_name, " +
                "    i.auctionStartTime AS item_start, " +
                "    i.auctionEndTime AS item_end, " +
                "    i.imgdata AS item_img " +
                "FROM auction_items a " +
                "LEFT JOIN items i ON a.id_item = i.my_row_id";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Auction auction = new Auction();
                auction.setItemId(rs.getLong("id_item"));

                model.Items.Item item = new model.Items.Item();
                item.setDatabaseId(rs.getInt("id_item"));
                item.setName(rs.getString("item_name"));
                item.setCurrentHighestPrice(rs.getDouble("currentPrice"));

                byte[] imgBytes = rs.getBytes("item_img");
                if (imgBytes != null && imgBytes.length > 0) {
                    item.setImg(new String(imgBytes, java.nio.charset.StandardCharsets.UTF_8));
                }

                Timestamp startTimestamp = rs.getTimestamp("item_start");
                Timestamp endTimestamp = rs.getTimestamp("item_end");

                if (startTimestamp != null) item.setAuctionStartTime(startTimestamp.toInstant());
                if (endTimestamp != null) item.setAuctionEndTime(endTimestamp.toInstant());

                auction.setItem(item);

                String statusStr = rs.getString("status");
                auction.setStatus(parseAuctionStatus(statusStr));

                String leadingUsername = rs.getString("leadingbider");
                if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                    auction.setLeadingBidder(leadingUsername);
                }

                String historyJson = rs.getString("bidHistory");
                if (historyJson != null && !historyJson.isEmpty()) {
                    try {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                        List<BidTransaction> history = gson.fromJson(historyJson, listType);
                        auction.setBidHistory(history);
                    } catch (Exception e) {
                        auction.setBidHistory(new ArrayList<>());
                    }
                }
                list.add(auction);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    private Auction mapResultSetToAuction(ResultSet rs, Item item) throws SQLException {
        Auction auction = new Auction();
        auction.setItemId(rs.getLong("id_item"));

        item.setCurrentHighestPrice(rs.getDouble("currentPrice"));
        auction.setItem(item);

        String statusStr = rs.getString("status");
        auction.setStatus(parseAuctionStatus(statusStr));

        String leadingUsername = rs.getString("leadingbider");
        if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
            auction.setLeadingBidder(leadingUsername);
        }

        String historyJson = rs.getString("bidHistory");
        if (historyJson != null && !historyJson.isEmpty()) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                List<BidTransaction> history = gson.fromJson(historyJson, listType);
                auction.setBidHistory(history);
            } catch (Exception e) {
                auction.setBidHistory(new ArrayList<>());
            }
        }
        return auction;
    }

    private AuctionStatus parseAuctionStatus(String statusStr) {
        if (statusStr == null) return null;
        String cleanStatus = statusStr.replace("\"", "").trim().toUpperCase();
        if (cleanStatus.isEmpty() || "NULL".equals(cleanStatus)) return null;
        try {
            return AuctionStatus.valueOf(cleanStatus);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int Delete(Item item) {
        String sql = "DELETE FROM auction_items WHERE id_item = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, item.getDatabaseId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * ✅ ĐÃ THÊM MỚI: Nhận Connection truyền vào từ Service để chạy chung Transaction.
     * Giải quyết dứt điểm lỗi biên dịch "Expected 1 argument but found 2"
     */
    public int updatePriceByItemIdWhenEditItem(Connection con, Item item) throws SQLException {
        String sql = "UPDATE auction_items SET currentPrice = ? WHERE id_item = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, item.getCurrentHighestPrice());
            pstmt.setLong(2, item.getDatabaseId());
            return pstmt.executeUpdate();
        }
    }
    public int Insert(Connection con, Auction auction, Item item) throws SQLException {
        String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingbider, bidHistory, currentPrice) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setLong(1, item.getDatabaseId());
            pstmt.setString(2, item.getSellerId());

            // Đọc trạng thái enum dạng text thuần (Theo logic tối ưu hóa lưu trữ trực tiếp)
            String rawStatus = auction.getStatus() != null ? auction.getStatus().name() : AuctionStatus.OPEN.name();
            pstmt.setString(3, rawStatus);

            pstmt.setString(4, auction.getLeadingBidder());
            pstmt.setString(5, gson.toJson(new ArrayList<model.auction.BidTransaction>()));
            pstmt.setDouble(6, item.getCurrentHighestPrice());

            return pstmt.executeUpdate();
        }
    }

}