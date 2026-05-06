package dao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.JDBCUtil;
import model.Items.Art;
import model.Items.Item;
import model.Items.ItemType;
import model.Items.Vehicle;
import model.User.Bidder;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;

/** Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 *Lây dữ liệu tương tác với database
 */
/**
 * DAO thao tác bảng items.
 *
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
    /** Snapshot user đăng nhập ở nhánh JavaFX UI; không dùng trong luồng socket server. */
    User p1 = UserSession.getLoggedInUser();

    @Override
    //Logic thêm sản phẩm__ dùng PrepareStatement
    /**
     * Precondition: item có các field cơ bản, seller id, khoảng thời gian, dữ liệu ảnh và loại item.
     * Postcondition: Insert một dòng vào bảng items và copy primary key sinh ra về item.databaseId.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu insert lỗi.
     */
    public int Insert(Item item) {
        Connection con = JDBCUtil.getConnection();
        String sql = "INSERT INTO items (name, startingPrice, sellerId, description, itemType, auctionStartTime, auctionEndTime, imgdata, currentHighestBid) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);) {
            // 1. name (String)
            pstmt.setString(1, item.getName());

            // 2. startingPrice (Double/Float)
            pstmt.setDouble(2, item.getStartingPrice());

            // 3. sellerId (Int)
            pstmt.setString(3, item.getSellerId());

            // 4. description (String)
            String jsonProperties = mapper.writeValueAsString(item.getProperties());

            // Đẩy chuỗi JSON này vào tham số thứ 6
            pstmt.setString(4, jsonProperties);
            // 5. itemType (String)
            pstmt.setString(5, item.getItemType());

            // Hầu hết các Driver hiện đại hỗ trợ trực tiếp setObject cho Instant
            Instant inst1 = item.getAuctionStartTime();
            Instant inst2 = item.getAuctionEndTime();
            if ( inst1 != null) {
                // CHÍNH LÀ ĐÂY: Chuyển Instant sang Timestamp để SQL hiểu
                pstmt.setTimestamp(6, java.sql.Timestamp.from(inst1));
            } else {
                pstmt.setNull(6, java.sql.Types.TIMESTAMP);
            }
            if ( inst2 != null) {

                pstmt.setTimestamp(7, java.sql.Timestamp.from(inst2));
            } else {
                pstmt.setNull(7, java.sql.Types.TIMESTAMP);}
            // 8 Truyền luồng dữ liệu nhị phân của ảnh vào câu lệnh SQL
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
    }

    @Override
    /**
     * Precondition: Không được implement cho DAOItems.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    public int Update(Item item) {
        return 0;
    }

    @Override
    /**
     * Precondition: Không được implement cho DAOItems.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    public int Insert(Auction auction, Item item1) {
        return 0;
    }


    /**
     * Precondition: item.databaseId xác định một dòng items tồn tại, Pricecurrent là giá cao nhất
     * vừa được chấp nhận.
     * Postcondition: Cập nhật items.currentHighestBid cho dòng đó.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu SQLException.
     */
    public int Update(Item item, Double Pricecurrent) {
        // ✅ FIXED: Cập nhật cột currentHighestBid (tên cột thực tế trong DB)
        String sql = "UPDATE items SET currentHighestBid = ? WHERE my_row_id = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // 1. Set giá tiền mới (người vừa trả cao nhất)
            pstmt.setDouble(1, Pricecurrent);
            // 4. Xác định cập nhật cho món hàng nào dựa trên ID (BIGINT)
            pstmt.setLong(2, item.getDatabaseId());

            // Thực thi lệnh và trả về số dòng bị ảnh hưởng (thường là 1 nếu thành công)
            int result = pstmt.executeUpdate();

            // Log để debug
            if (result > 0) {
                System.out.println("✅ Cập nhật giá item ID " + item.getDatabaseId() + " thành công: " + Pricecurrent);
            } else {
                System.err.println("❌ Không update được giá item ID " + item.getDatabaseId());
            }

            return result;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi UPDATE item: " + e.getMessage());
            e.printStackTrace();
            return 0; // Trả về 0 nếu có lỗi xảy ra
        }
    }



    @Override
    /**
     * Precondition: Không được implement cho DAOItems.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    public int Delete(Item item) {
        return 0;
    }

    @Override
    /**
     * Precondition: API instance này chưa được implement cho DAOItems.
     * Postcondition: Method trả null.
     */
    public ArrayList<Item> selectAll(){
        return null;
    }

    /**
     * Precondition: Có thể tạo kết nối database và bảng items tồn tại.
     * Postcondition: Method trả về toàn bộ item được map từ bảng items, hoặc null nếu SQLException.
     */
    public static ArrayList<Item> selectedAll(){
        ArrayList<Item> list = new ArrayList<>();
            String sql = "SELECT * FROM items";
            Connection con = JDBCUtil.getConnection();
        PreparedStatement pstmt = null;
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
        }

    @Override
    /**
     * Precondition: Không được implement cho DAOItems.
     * Postcondition: Method trả null.
     */
    public Item selectByUsername(Item item) {
        return null;
    }

    /**
     * Precondition: itemId là chuỗi số không null, khớp với items.my_row_id.
     * Postcondition: Method trả về Item đã map nếu tìm thấy; ngược lại trả null.
     * NOTE: Chuỗi không parse được số sẽ bị catch và ghi log.
     */
    public Item selectById(String itemId) {
        // ✅ FIX: Validate itemId không rỗng
        if (itemId == null || itemId.isEmpty() || "null".equals(itemId)) {
            System.err.println("❌ selectById: itemId rỗng hoặc null");
            return null;
        }

        Connection con = JDBCUtil.getConnection();
        String sql = "SELECT * FROM items WHERE my_row_id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(itemId));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Item item = mapResultSetToItem(rs);
                return item;
            }
        } catch (NumberFormatException e) {
            System.err.println("❌ selectById: itemId không phải số: " + itemId);
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
        double currentHighestPrice = rs.getDouble("currentHighestBid");
        Item item = new Item(name, description, startingPrice, sellerId, imgData, itemType);
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


    @Override
    /**
     * Precondition: Không được implement cho DAOItems.
     * Postcondition: Method trả null.
     */
    public ArrayList selectByCondition(String condition) {
        return null;
    }
}
