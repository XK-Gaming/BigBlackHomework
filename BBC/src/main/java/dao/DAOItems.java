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
public class DAOItems implements DaoInterface<Item> {
    static final ObjectMapper mapper = new ObjectMapper();
    public static DAOItems getInstance() {
        return new DAOItems();
    }
    User p1 = UserSession.getLoggedInUser();
    @Override
    //Logic thêm sản phẩm__ dùng PrepareStatement
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

            pstmt.executeUpdate();
            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int generatedId = rs.getInt(1);
                item.setId(generatedId); // Gán ngược lại vào đối tượng
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Luôn đóng kết nối trong finally hoặc dùng try-with-resources
            JDBCUtil.closeConnection(con);
        }
        return 0;
    }

    @Override
    public int Update(Item item) {
        return 0;
    }

    @Override
    public int Insert(Auction auction, Item item1) {
        return 0;
    }


    public int Update(Item item, Double Pricecurrent) {
        String sql = "UPDATE items SET currentHighestBid = ? WHERE my_row_id = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // 1. Set giá tiền mới (người vừa trả cao nhất)
            pstmt.setDouble(1, item.getCurrentHighestPrice());
            // 4. Xác định cập nhật cho món hàng nào dựa trên ID (BIGINT)
            pstmt.setLong(2, item.getId_Item());

            // Thực thi lệnh và trả về số dòng bị ảnh hưởng (thường là 1 nếu thành công)
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0; // Trả về 0 nếu có lỗi xảy ra
        }
    }



    @Override
    public int Delete(Item item) {
        return 0;
    }

    @Override
    public ArrayList<Item> selectAll(){
        return null;
    }

    public static ArrayList<Item> selectedAll(){
        ArrayList<Item> list = new ArrayList<>();
            String sql = "SELECT * FROM items";
            Connection con = JDBCUtil.getConnection();
        PreparedStatement pstmt = null;
        try {
            pstmt = con.prepareStatement(sql);

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            Item item = new Item() {
                @Override
                public String printInfo() {
                    return "";
                }
            };
            // Lấy dữ liệu cơ bản
            item.setId(rs.getInt("my_row_id"));
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
    public Item selectByUsername(Item item) {
        return null;
    }


    @Override
    public ArrayList selectByCondition(String condition) {
        return null;
    }
}
