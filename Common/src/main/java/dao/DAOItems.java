package dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import database.JDBCUtil;
import model.Items.Item;
import model.Items.ItemType;

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

    public static DAOItems getInstance() {
        return new DAOItems();
    }

    @Override
    public int Insert(Item item) {
        try (Connection con = JDBCUtil.getConnection()) {
            String sql = "INSERT INTO items (name, startingPrice, sellerId, description, itemType, auctionStartTime, auctionEndTime, imgdata, currentHighestBid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                pstmt.setString(1, item.getName());
                pstmt.setDouble(2, item.getStartingPrice());
                pstmt.setString(3, item.getSellerId());

                // 🌟 ĐỒNG BỘ JSON: Đóng gói cả description và các trường phụ (properties) vào chung 1 chuỗi JSON
                Map<String, String> payload = new HashMap<>();
                payload.put("description", item.getDescription()); // Lấy văn bản mô tả thật bỏ vào key "description"
                if (item.getProperties() != null) {
                    payload.putAll(item.getProperties()); // Gom thêm artist, model, year,... vào chung payload
                }
                String combinedJson = mapper.writeValueAsString(payload);
                pstmt.setString(4, combinedJson);

                // 🌟 ĐỒNG BỘ ITEMTYPE: Lưu trực tiếp chuỗi tiếng Việt có dấu của bạn ("Mỹ thuật",...) vào DB
                pstmt.setString(5, item.getRawItemType() != null ? item.getRawItemType().toString() : null);

                Instant inst1 = item.getAuctionStartTime();
                if (inst1 != null) {
                    pstmt.setTimestamp(6, java.sql.Timestamp.from(inst1));
                } else {
                    pstmt.setNull(6, java.sql.Types.TIMESTAMP);
                }

                Instant inst2 = item.getAuctionEndTime();
                if (inst2 != null) {
                    pstmt.setTimestamp(7, java.sql.Timestamp.from(inst2));
                } else {
                    pstmt.setNull(7, java.sql.Types.TIMESTAMP);
                }

                pstmt.setString(8, item.getImg());
                pstmt.setDouble(9, item.getStartingPrice());

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

    public int Update(Item item) {
        String sql = "UPDATE items SET name = ?, description = ?, startingPrice = ?, auctionStartTime = ?, auctionEndTime = ?, imgdata = ?, itemType = ? WHERE my_row_id = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, item.getName());

            // 🌟 GIẢI PHÁP AN TOÀN: Tạo bản đồ Payload để đóng gói JSON
            Map<String, String> payload = new HashMap<>();
            payload.put("description", item.getDescription()); // Luôn lấy mô tả mới nhất (bao gồm cả note nếu có)

            // Lấy Map thuộc tính hiện tại của đối tượng item
            Map<String, String> currentProps = item.getProperties();

            // 🛡️ PHÒNG VỆ ĐA LUỒNG / TRÁNH MẤT DATA:
            // Nếu các trường phụ bị null hoặc rỗng hoàn toàn (do UI khóa không truyền xuống khi Append Note),
            // ta sẽ chủ động lấy lại các thuộc tính cũ đang nằm trong DB để không làm mất dữ liệu lịch sử của item.
            if (currentProps == null || (currentProps.get("artist") == null && currentProps.get("brand") == null && currentProps.get("manufacturer") == null)) {
                Item dbBackup = this.selectById(con, String.valueOf(item.getDatabaseId()));
                if (dbBackup != null && dbBackup.getProperties() != null) {
                    currentProps = dbBackup.getProperties();
                }
            }

            // Đổ toàn bộ dữ liệu thuộc tính an toàn vào JSON payload
            if (currentProps != null) {
                payload.putAll(currentProps);
            }

            // Tiến hành chuyển đổi sang chuỗi JSON để lưu trữ vào trường description
            String combinedJson = mapper.writeValueAsString(payload);
            pstmt.setString(2, combinedJson);

            pstmt.setDouble(3, item.getStartingPrice());

            Instant startTime = item.getAuctionStartTime();
            pstmt.setTimestamp(4, startTime != null ? java.sql.Timestamp.from(startTime) : null);

            Instant endTime = item.getAuctionEndTime();
            pstmt.setTimestamp(5, endTime != null ? java.sql.Timestamp.from(endTime) : null);

            pstmt.setString(6, item.getImg());

            // Đảm bảo lưu đúng giá trị chuỗi tiếng Việt hoặc giá trị chuỗi của Enum
            pstmt.setString(7, item.getRawItemType() != null ? item.getRawItemType().toString() : null);
            pstmt.setInt(8, item.getDatabaseId());

            return pstmt.executeUpdate();

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

    public ArrayList<Item> selectAll() {
        ArrayList<Item> list = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Item item = mapResultSetToItem(rs);
                if (item != null) list.add(item);
            }
            return list;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Item selectById(String itemId) {
        try (Connection con = JDBCUtil.getConnection()) {
            String sql = "SELECT * FROM items WHERE my_row_id = ?";
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(itemId));
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToItem(rs);
                    }
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * 🌟 HÀM CỐT LÕI: Giải mã chuỗi JSON từ cột description và đẩy ngược lại vào UI
     */
    @SuppressWarnings("unchecked")
    private Item mapResultSetToItem(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setDatabaseId(rs.getInt("my_row_id"));
        item.setName(rs.getString("name"));
        item.setStartingPrice(rs.getDouble("startingPrice"));
        item.setSellerId(rs.getString("sellerId"));
        item.setImg(rs.getString("imgdata"));
        item.setCurrentHighestPrice(rs.getDouble("currentHighestBid"));

        // 1. Phân rã dữ liệu từ chuỗi JSON nằm trong cột description
        String jsonDescription = rs.getString("description");
        if (jsonDescription != null && (jsonDescription.trim().startsWith("{") || jsonDescription.trim().startsWith("["))) {
            try {
                // Đọc chuỗi JSON biến ngược lại thành Map
                Map<String, String> rawMap = mapper.readValue(jsonDescription, Map.class);

                // Trích xuất văn bản mô tả thực tế hiển thị lên form Edit
                item.setDescription(rawMap.getOrDefault("description", ""));

                // Trích xuất các trường mở rộng nhét lại vào Map Properties của Item
                Map<String, String> props = item.getProperties();
                props.put("brand", rawMap.get("brand"));
                props.put("model", rawMap.get("model"));
                props.put("manufacturer", rawMap.get("manufacturer"));
                props.put("year", rawMap.get("year"));
                props.put("artist", rawMap.get("artist"));

            } catch (Exception e) {
                // Fallback nếu chuỗi text thô không phải cấu trúc JSON hợp lệ
                item.setDescription(jsonDescription);
            }
        } else {
            item.setDescription(jsonDescription);
        }

        // 2. Đồng bộ loại mặt hàng từ chuỗi Tiếng Việt dưới DB
        String itemTypeStr = rs.getString("itemType");
        item.setItemType(ItemType.fromString(itemTypeStr));

        Timestamp startTime = rs.getTimestamp("auctionStartTime");
        if (startTime != null) item.setAuctionStartTime(startTime.toInstant());

        Timestamp endTime = rs.getTimestamp("auctionEndTime");
        if (endTime != null) item.setAuctionEndTime(endTime.toInstant());

        return item;
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
}
