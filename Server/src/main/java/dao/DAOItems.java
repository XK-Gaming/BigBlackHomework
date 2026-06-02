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

/* Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 Lấy dữ liệu tương tác với database
 */
/**
 * DAO thao tác bảng items bám sát cấu trúc dữ liệu JSON thô trong cột description.
 */
public class DAOItems implements DaoInterface<Item> {

    /** Jackson mapper dùng để serialize/deserialize các thuộc tính trong chuỗi JSON */
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

                // 🌟 ĐỒNG BỘ JSON: Đóng gói cả description và các trường phụ (properties) vào chung 1 chuỗi JSON
                Map<String, String> payload = new HashMap<>();
                payload.put("description", item.getDescription()); // Lấy văn bản mô tả thật bỏ vào key "description"
                if (item.getProperties() != null) {
                    payload.putAll(item.getProperties()); // Gom thêm artist, model, year,... vào chung payload
                }
                String combinedJson = mapper.writeValueAsString(payload);
                pstmt.setString(5, combinedJson);

                // 🌟 ĐỒNG BỘ ITEMTYPE: Lưu trực tiếp chuỗi tiếng Việt có dấu của bạn ("Mỹ thuật",...) vào DB
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

                // 🌟 GIẢI PHÁP AN TOÀN: Tạo bản đồ Payload để đóng gói JSON
                Map<String, String> payload = new HashMap<>();
                payload.put("description", item.getDescription());

                Map<String, String> currentProps = item.getProperties();

                // 🛡️ PHÒNG VỆ ĐA LUỒNG / TRÁNH MẤT DATA:
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

                // ⬇️ ĐÃ FIX THỨ TỰ TỪ ĐÂY ⬇️

                // Tham số số 9: currentHighestBid (Lấy giá khởi điểm hoặc getter tương ứng của bạn)
                pstmt.setDouble(9, item.getStartingPrice());

                // Tham số số 10: Điều kiện WHERE my_row_id = ?
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

    public Item selectById(Connection con, String itemId) throws SQLException {
        String sql = "SELECT * FROM items WHERE my_row_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            }
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

    /**
     * Precondition: Có thể tạo kết nối database và bảng items tồn tại.
     * Postcondition: Method trả về toàn bộ item được map từ bảng items, hoặc null nếu SQLException.
     */
    public ArrayList<Item> selectAll() {
        ArrayList<Item> list = new ArrayList<>();

        String sql = "SELECT i.my_row_id AS item_id, i.name, i.startingPrice, i.minBid, i.sellerId, i.description, " +
                "i.itemType, i.auctionStartTime, i.auctionEndTime, i.imgdata, i.currentHighestBid, " +
                "JSON_UNQUOTE(a.status) AS clean_status " +
                "FROM items i " +
                "LEFT JOIN auction_items a ON i.my_row_id = a.id_item";

        try (Connection con = JDBCUtil.getConnection()) {
            ensureMinBidColumn(con);
            try (PreparedStatement pstmt = con.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                while (rs.next()) {
                    Item item = new Item();

                    // ==========================================
                    // XỬ LÝ TRẠNG THÁI (STATUS) - ĐÃ SỬA LỖI LOGIC
                    // ==========================================
                    String statusStr = rs.getString("clean_status");
                    if (statusStr == null || statusStr.trim().isEmpty() || "null".equalsIgnoreCase(statusStr.trim())) {
                        item.setAuctionStatus(null);
                    } else {
                        String cleanedStatus = statusStr.trim().toUpperCase();
                        try {
                            item.setAuctionStatus(AuctionStatus.valueOf(cleanedStatus));
                        } catch (IllegalArgumentException e) {
                            item.setAuctionStatus(null);
                        }
                    }

                    // ==========================================
                    // XỬ LÝ CÁC DỮ LIỆU CƠ BẢN CỦA ITEM
                    // ==========================================
                    item.setDatabaseId(rs.getInt("item_id"));
                    item.setName(rs.getString("name"));
                    item.setSellerId(rs.getString("sellerId"));
                    item.setDescription(rs.getString("description"));

                    // Ép kiểu enum cho ItemType an toàn
                    String typeStr = rs.getString("itemType");
                    if (typeStr != null) {
                        item.setItemType(ItemType.fromString(typeStr));
                    }

                    // Xử lý giá tiền (Có check NULL từ DB)
                    double startingPrice = rs.getDouble("startingPrice");
                    item.setStartingPrice(rs.wasNull() ? null : startingPrice);

                    double minBid = rs.getDouble("minBid");
                    item.setMinBid(rs.wasNull() ? 0 : minBid);

                    double currentHighestBid = rs.getDouble("currentHighestBid");
                    item.setCurrentHighestPrice(rs.wasNull() ? null : currentHighestBid);

                    // ==========================================
// XỬ LÝ THỜI GIAN (TIMESTAMP -> INSTANT)
                    // ==========================================
                    Timestamp startTs = rs.getTimestamp("auctionStartTime");
                    if (startTs != null) item.setAuctionStartTime(startTs.toInstant());

                    Timestamp endTs = rs.getTimestamp("auctionEndTime");
                    if (endTs != null) item.setAuctionEndTime(endTs.toInstant());

                    // ==========================================
                    // XỬ LÝ ẢNH (LONGBLOB LƯU URL)
                    // ==========================================
                    byte[] imgBytes = rs.getBytes("imgdata");
                    if (imgBytes != null && imgBytes.length > 0) {
                        String imgUrl = new String(imgBytes, java.nio.charset.StandardCharsets.UTF_8);
                        item.setImg(imgUrl);
                    } else {
                        item.setImg(null);
                    }

                    // Đưa đối tượng hoàn chỉnh vào danh sách
                    list.add(item);
                }

            }
            return list;

        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn SQL tại hàm selectAll: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>(); // Nên trả về list rỗng thay vì null để tránh lỗi NullPointerException ở hàm gọi nó
        }
    }

    private AuctionStatus parseAuctionStatus(String statusStr) {
        if (statusStr == null) {
            return null;
        }

        String cleanStatus = statusStr.replace("\"", "").trim().toUpperCase();
        if (cleanStatus.isEmpty() || "NULL".equals(cleanStatus)) {
            return null;
        }

        try {
            return AuctionStatus.valueOf(cleanStatus);
        } catch (IllegalArgumentException e) {
            System.err.println("Status dau gia khong hop le trong DB: " + statusStr);
            return null;
        }
    }

    /**
     * Precondition: itemId là chuỗi số không null, khớp với items.my_row_id.
     * Postcondition: Method trả về Item đã map nếu tìm thấy; ngược lại trả null.
     * NOTE: Chuỗi không parse được số sẽ bị catch và ghi log.
     */

    public Item selectById(String itemId) {

        try (Connection con = JDBCUtil.getConnection();) {
            String sql = "SELECT * FROM items WHERE my_row_id = ?";
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(itemId));
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    Item item = mapResultSetToItem(rs);
                    return item;
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Precondition: rs đang trỏ tới một dòng hợp lệ trong bảng items.
     * Postcondition: Method trả về Item được populate từ dòng ResultSet hiện tại.
     * NOTE: Method tạo base Item, không tạo subtype như Art hay Vehicle.
     */
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        String itemType = rs.getString("itemType");
        String name = rs.getString("name");
        String description = rs.getString("description");
        double startingPrice = rs.getDouble("startingPrice");
        String sellerId = rs.getString("sellerId");
        Timestamp startTime = rs.getTimestamp("auctionStartTime");
        Timestamp endTime = rs.getTimestamp("auctionEndTime");
        String imgData = rs.getString("imgdata");
        double minBid = readOptionalDouble(rs, "minBid", 0);
        double currentHighestPrice = rs.getDouble("currentHighestBid");

        // 🌟 SỬA TẠI ĐÂY: Ép kiểu Enum an toàn, chống crash luồng khi đọc dữ liệu từ DB
        ItemType typeEnum = null;
        if (itemType != null && !itemType.trim().isEmpty()) {
            try {
                // Ưu tiên dùng hàm tự chế từ chuỗi tiếng Việt của bạn (nếu có trong ItemType)
                typeEnum = ItemType.fromString(itemType);
            } catch (Exception e) {
                try {
                    // Phương án dự phòng 2: Khớp theo tên Enum chuẩn (Chuyển hoa để tránh lệch định dạng)
                    typeEnum = ItemType.valueOf(itemType.trim().toUpperCase());
                } catch (IllegalArgumentException ex) {
                    System.err.println("⚠️ Cảnh báo: Không thể map loại sản phẩm '" + itemType + "' sang Enum ItemType!");
                    typeEnum = null; // Hoặc gán một giá trị mặc định tùy hệ thống của bạn
                }
            }
        }

        // Khởi tạo Item với typeEnum an toàn đã được xử lý
        Item item = new Item(name, description, startingPrice, minBid, sellerId, imgData, typeEnum);

        // Gán ID từ cột my_row_id
        item.setDatabaseId(rs.getInt("my_row_id"));

        if (startTime != null) {
            item.setAuctionStartTime(startTime.toInstant());
        }
        if (endTime != null) {
            item.setAuctionEndTime(endTime.toInstant());
        }

        item.setCurrentHighestPrice(currentHighestPrice);
        return item;
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
        if (minBidColumnChecked) {
            return;
        }
        synchronized (DAOItems.class) {
            if (minBidColumnChecked) {
                return;
            }
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