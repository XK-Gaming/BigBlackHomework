package dao;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.JDBCUtil;
import model.Items.Item;
import model.Items.ItemType;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;

/* Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 Lây dữ liệu tương tác với database
 */
/**
 * DAO thao tác bảng items.
 * Trách nhiệm class: insert item đấu giá, cập nhật giá cao nhất hiện tại, và map dữ liệu
 * từ bảng items về Item object.
 */
public class DAOItems implements DaoInterface<Item> {
    /** Jackson mapper dùng để serialize các thuộc tính riêng của subclass item. */
    static final ObjectMapper mapper = new ObjectMapper();
    /**
     * Precondition: Không có.
     * Postcondition: Method trả về một instance DAOItems mới.
     */
    public static DAOItems getInstance() {
        return new DAOItems();
    }

    @Override
    public int Insert(Item item)  {
        try (Connection con = JDBCUtil.getConnection()) {
            String sql = "INSERT INTO items (name, startingPrice, sellerId, description, itemType, auctionStartTime, auctionEndTime, imgdata, currentHighestBid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, item.getName());

                pstmt.setDouble(2, item.getStartingPrice());

                pstmt.setString(3, item.getSellerId());

                String jsonProperties = mapper.writeValueAsString(item.getProperties());

                pstmt.setString(4, jsonProperties);
                pstmt.setString(5, item.getItemType());

                // Hầu hết các Driver hiện đại hỗ trợ trực tiếp setObject cho Instant
                Instant inst1 = item.getAuctionStartTime();
                Instant inst2 = item.getAuctionEndTime();
                if ( inst1 != null) {
                    //Chuyển Instant sang Timestamp để SQL hiểu
                    pstmt.setTimestamp(6, java.sql.Timestamp.from(inst1));
                } else {
                    pstmt.setNull(6, java.sql.Types.TIMESTAMP);
                }
                if ( inst2 != null) {

                    pstmt.setTimestamp(7, java.sql.Timestamp.from(inst2));
                } else {
                    pstmt.setNull(7, java.sql.Types.TIMESTAMP);}
                // Truyền luồng dữ liệu nhị phân của ảnh vào câu lệnh SQL
                pstmt.setString(8,item.getImg());
                pstmt.setDouble(9,item.getStartingPrice());

                int rowsAffected = pstmt.executeUpdate();
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    int generatedId = rs.getInt(1);
                    item.setDatabaseId(generatedId); // Gán ngược lại vào đối tượng
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
    /*
      Precondition: item.databaseId xác định một dòng items tồn tại, Pricecurrent là giá cao nhất
      vừa được chấp nhận.
      Postcondition: Cập nhật items.currentHighestBid cho dòng đó.
      Connection được truyền từ Service và không bị đóng tại đây.
     */
    public int Update(Connection con, Item item) throws SQLException {
        String sql = "UPDATE items SET currentHighestBid = ? WHERE my_row_id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, item.getCurrentHighestPrice());
            pstmt.setLong(2, item.getDatabaseId());
            return pstmt.executeUpdate();
        }
    }



    /**
     * Precondition: Có thể tạo kết nối database và bảng items tồn tại.
     * Postcondition: Method trả về toàn bộ item được map từ bảng items, hoặc null nếu SQLException.
     */
    public ArrayList<Item> selectAll(){
        ArrayList<Item> list = new ArrayList<>();
        String sql = "SELECT * FROM items";
        try (Connection con = JDBCUtil.getConnection()) {
            PreparedStatement pstmt ;
            try {
                pstmt = con.prepareStatement(sql);

                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    Item item = new Item();
                    // Lấy dữ liệu cơ bản
                    item.setDatabaseId(rs.getInt("my_row_id"));
                    item.setName(rs.getString("name"));
                    item.setStartingPrice(rs.getDouble("startingPrice"));
                    item.setSellerId(rs.getString("sellerId"));
                    item.setDescription(rs.getString("description"));
                    item.setItemType(ItemType.fromString(rs.getString("itemType")));
                    // Chuyển đổi Timestamp (SQL) -> Instant (Java)
                    Timestamp startTs = rs.getTimestamp("auctionStartTime");
                    if (startTs != null) item.setAuctionStartTime(startTs.toInstant());

                    Timestamp endTs = rs.getTimestamp("auctionEndTime");
                    if (endTs != null) item.setAuctionEndTime(endTs.toInstant());
                    String img = rs.getString("imgdata");
                    item.setImg(img);
                    item.setCurrentHighestPrice(rs.getDouble("currentHighestBid"));
                    list.add(item);}


                return list;
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ArrayList<Item> moreSelectByCondition(String condition) {
        return null;
    }

    /**
     * Precondition: itemId là chuỗi số không null, khớp với items.my_row_id.
     * Postcondition: Method trả về Item đã map nếu tìm thấy; ngược lại trả null.
     * NOTE: Chuỗi không parse được số sẽ bị catch và ghi log.
     */

    public Item selectById(String itemId) {

        try (Connection con = JDBCUtil.getConnection()) {
            String sql = "SELECT * FROM items WHERE my_row_id = ?";
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setInt(1, Integer.parseInt(itemId));
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return mapResultSetToItem(rs);
                }
            } catch (NumberFormatException e) {
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
        String itemTypeStr = rs.getString("itemType");
        String name = rs.getString("name");
        String description = rs.getString("description");
        double startingPrice = rs.getDouble("startingPrice");
        String sellerId = rs.getString("sellerId");
        Timestamp startTime = rs.getTimestamp("auctionStartTime");
        Timestamp endTime = rs.getTimestamp("auctionEndTime");
        String imgData = rs.getString("imgdata");
        double currentHighestPrice = rs.getDouble("currentHighestBid");
        Item item = new Item();
        item.setDatabaseId(rs.getInt("my_row_id"));
        item.setName(name);
        item.setDescription(description);
        item.setStartingPrice(startingPrice);
        item.setSellerId(sellerId);
        item.setImg(imgData);
        ItemType mappedType = ItemType.fromString(itemTypeStr);
        if (mappedType != null) {
            item.setItemType(mappedType);
        }
        if (startTime != null) {
            item.setAuctionStartTime(startTime.toInstant());
        }
        if (endTime != null) {
            item.setAuctionEndTime(endTime.toInstant());
        }
        item.setCurrentHighestPrice(currentHighestPrice);
        return item;
    }
    // ✅ SELECT bình thường, không cần FOR UPDATE
    public Item selectById(Connection con, String itemId) throws SQLException {
        String sql = "SELECT * FROM items WHERE my_row_id = ?";
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setString(1, itemId);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            return mapResultSetToItem(rs);
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


}