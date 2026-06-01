package dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import database.JDBCUtil;
import model.Items.Item;
import model.Items.ItemType;
import model.auction.AuctionStatus;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DAOItems implements DaoInterface<Item> {

    static final ObjectMapper mapper = new ObjectMapper();
    private static volatile boolean minBidColumnChecked;

    public static DAOItems getInstance() {
        return new DAOItems();
    }

    @Override
    public int Insert(Item item) {
        try (Connection con = JDBCUtil.getConnection()) {
            ensureMinBidColumn(con);
            String sql = "INSERT INTO items (name, startingPrice, minBid, sellerId, description, itemType, auctionStartTime, auctionEndTime, imgdata, currentHighestBid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, item.getName());
                pstmt.setDouble(2, item.getStartingPrice());
                pstmt.setDouble(3, item.getMinBid());
                pstmt.setString(4, item.getSellerId());

                Map<String, String> payload = new HashMap<>();
                payload.put("description", item.getDescription());
                if (item.getProperties() != null) {
                    payload.putAll(item.getProperties());
                }
                String combinedJson = mapper.writeValueAsString(payload);
                pstmt.setString(5, combinedJson);

                pstmt.setString(6, item.getRawItemType() != null ? item.getRawItemType().toString() : null);

                Instant inst1 = item.getAuctionStartTime();
                if (inst1 != null) {
                    pstmt.setTimestamp(7, java.sql.Timestamp.from(inst1));
                } else {
                    pstmt.setNull(7, java.sql.Types.TIMESTAMP);
                }

                Instant inst2 = item.getAuctionEndTime();
                if (inst2 != null) {
                    pstmt.setTimestamp(8, java.sql.Timestamp.from(inst2));
                } else {
                    pstmt.setNull(8, java.sql.Types.TIMESTAMP);
                }

                pstmt.setString(9, item.getImg());
                pstmt.setDouble(10, item.getStartingPrice());

                int rowsAffected = pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int generatedId = rs.getInt(1);
                        item.setDatabaseId(generatedId);
                    }
                }
                return rowsAffected;
            } catch (Exception e) {
                e.printStackTrace();
                return 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * ✅ ĐÃ ĐỒNG BỘ: Sử dụng Connection từ Service và bọc cơ chế ghi đè dữ liệu cập nhật.
     */
    @Override
    public int Update(Connection con, Item item) throws SQLException {
        String sql = "UPDATE items SET currentHighestBid = ?, auctionEndTime = ? WHERE my_row_id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, item.getCurrentHighestPrice());
            if (item.getAuctionEndTime() == null) {
                pstmt.setNull(2, Types.TIMESTAMP);
            } else {
                pstmt.setTimestamp(2, Timestamp.from(item.getAuctionEndTime()));
            }
            pstmt.setLong(3, item.getDatabaseId());
            return pstmt.executeUpdate();
        }
    }

    public int UpdateWhenEdit(Item item) {
        String sql = "UPDATE items SET name = ?, description = ?, startingPrice = ?, minBid = ?, " +
                "auctionStartTime = ?, auctionEndTime = ?, imgdata = ?, itemType = ?, " +
                "currentHighestBid = ? WHERE my_row_id = ?";

        try (Connection con = JDBCUtil.getConnection()) {
            ensureMinBidColumn(con);
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setString(1, item.getName());

                Map<String, String> payload = new HashMap<>();
                payload.put("description", item.getDescription());

                Map<String, String> currentProps = item.getProperties();

                if (currentProps == null || (currentProps.get("artist") == null && currentProps.get("brand") == null && currentProps.get("manufacturer") == null)) {
                    Item dbBackup = this.selectById(con, String.valueOf(item.getDatabaseId()));
                    if (dbBackup != null && dbBackup.getProperties() != null) {
                        currentProps = dbBackup.getProperties();
                    }
                }

                if (currentProps != null) {
                    payload.putAll(currentProps);
                }

                String combinedJson = mapper.writeValueAsString(payload);
                pstmt.setString(2, combinedJson);

                pstmt.setDouble(3, item.getStartingPrice());
                pstmt.setDouble(4, item.getMinBid());

                Instant startTime = item.getAuctionStartTime();
                pstmt.setTimestamp(5, startTime != null ? java.sql.Timestamp.from(startTime) : null);

                Instant endTime = item.getAuctionEndTime();
                pstmt.setTimestamp(6, endTime != null ? java.sql.Timestamp.from(endTime) : null);

                pstmt.setString(7, item.getImg());
                pstmt.setString(8, item.getRawItemType() != null ? item.getRawItemType().toString() : null);
                pstmt.setDouble(9, item.getStartingPrice());
                pstmt.setInt(10, item.getDatabaseId());

                return pstmt.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("Lỗi thực thi Update JSON tại sản phẩm có ID: " + item.getDatabaseId());
            e.printStackTrace();
            return 0;
        }
    }

    public ArrayList<Item> selectBySellerId(String sellerId) {
        ArrayList<Item> list = new ArrayList<>();
        String sql = "SELECT * FROM items WHERE sellerId = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, sellerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) list.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /**
     * ✅ ĐÃ SỬA LỖI CHÍ MẠNG: Kích hoạt Khóa bi quan "FOR UPDATE"
     * Khóa chặt hàng dữ liệu này dưới Database, chặn đứng hoàn toàn hiện tượng Race Condition đa máy chủ.
     */
    public Item selectById(Connection con, String itemId) throws SQLException {
        String sql = "SELECT * FROM items WHERE my_row_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Integer.parseInt(itemId));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Item item = mapResultSetToItem(rs);

                    // Lấy trạng thái đấu giá kèm theo từ bảng liên kết (nếu có)
                    String statusSql = "SELECT JSON_UNQUOTE(status) AS clean_status FROM auction_items WHERE id_item = ?";
                    try (PreparedStatement statusPs = con.prepareStatement(statusSql)) {
                        statusPs.setInt(1, item.getDatabaseId());
                        try (ResultSet statusRs = statusPs.executeQuery()) {
                            if (statusRs.next()) {
                                item.setAuctionStatus(parseAuctionStatus(statusRs.getString("clean_status")));
                            }
                        }
                    }
                    return item;
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("⚠️ Định dạng ID sản phẩm không hợp lệ: " + itemId);
        }
        return null;
    }

    @Override
    public int Delete(Item item) {
        String sql = "DELETE FROM items WHERE my_row_id = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, item.getDatabaseId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public ArrayList<Item> moreSelectByCondition(String condition) {
        return null;
    }

    public ArrayList<Item> selectAll() {
        ArrayList<Item> list = new ArrayList<>();
        String sql = "SELECT i.*, JSON_UNQUOTE(a.status) AS clean_status " +
                "FROM items i " +
                "LEFT JOIN auction_items a ON i.my_row_id = a.id_item";

        try (Connection con = JDBCUtil.getConnection()) {
            ensureMinBidColumn(con);
            try (PreparedStatement pstmt = con.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    if (item != null) {
                        item.setAuctionStatus(parseAuctionStatus(rs.getString("clean_status")));
                        list.add(item);
                    }
                }
            }
            return list;
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn SQL tại hàm selectAll: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Item selectById(String itemId) {
        try (Connection con = JDBCUtil.getConnection()) {
            return selectById(con, itemId);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi kết nối khi selectById độc lập", e);
        }
    }

    /**
     * ✅ TỐI ƯU HÓA & ĐỒNG BỘ logic map dữ liệu tập trung
     * Giải quyết triệt để lỗi phân tách sai định dạng byte[] / String giữa các hàm khác nhau.
     */
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        String itemType = rs.getString("itemType");
        String name = rs.getString("name");
        String description = rs.getString("description");
        double startingPrice = rs.getDouble("startingPrice");
        String sellerId = rs.getString("sellerId");
        Timestamp startTime = rs.getTimestamp("auctionStartTime");
        Timestamp endTime = rs.getTimestamp("auctionEndTime");

        // Đọc dữ liệu ảnh an toàn theo mảng byte rồi chuyển đổi chuỗi UTF-8
        byte[] imgBytes = rs.getBytes("imgdata");
        String imgData = (imgBytes != null && imgBytes.length > 0)
                ? new String(imgBytes, java.nio.charset.StandardCharsets.UTF_8)
                : null;

        double minBid = readOptionalDouble(rs, "minBid", 0);
        double currentHighestPrice = rs.getDouble("currentHighestBid");

        ItemType typeEnum = null;
        if (itemType != null && !itemType.trim().isEmpty()) {
            try {
                typeEnum = ItemType.fromString(itemType);
            } catch (Exception e) {
                try {
                    typeEnum = ItemType.valueOf(itemType.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    System.err.println("⚠️ Cảnh báo: Không thể map loại sản phẩm '" + itemType + "' sang Enum ItemType!");
                }
            }
        }

        Item item = new Item(name, description, startingPrice, minBid, sellerId, imgData, typeEnum);
        item.setDatabaseId(rs.getInt("my_row_id"));

        if (startTime != null) item.setAuctionStartTime(startTime.toInstant());
        if (endTime != null) item.setAuctionEndTime(endTime.toInstant());

        item.setCurrentHighestPrice(rs.wasNull() ? null : currentHighestPrice);
        return item;
    }

    private AuctionStatus parseAuctionStatus(String statusStr) {
        if (statusStr == null) return null;
        String cleanStatus = statusStr.replace("\"", "").trim().toUpperCase();
        if (cleanStatus.isEmpty() || "NULL".equals(cleanStatus)) return null;
        try {
            return AuctionStatus.valueOf(cleanStatus);
        } catch (IllegalArgumentException e) {
            System.err.println("Status đấu giá không hợp lệ trong DB: " + statusStr);
            return null;
        }
    }

    private static double readOptionalDouble(ResultSet rs, String columnName, double fallback) {
        try {
            double value = rs.getDouble(columnName);
            return rs.wasNull() ? fallback : value;
        } catch (SQLException e) {
            return fallback;
        }
    }

    private static void ensureMinBidColumn(Connection con) throws SQLException {
        if (minBidColumnChecked) return;
        synchronized (DAOItems.class) {
            if (minBidColumnChecked) return;
            try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, "items", "minBid")) {
                if (!rs.next()) {
                    try (Statement statement = con.createStatement()) {
                        statement.executeUpdate("ALTER TABLE items ADD COLUMN minBid DOUBLE NOT NULL DEFAULT 0");
                    }
                }
            }
            minBidColumnChecked = true;
        }
    }
}