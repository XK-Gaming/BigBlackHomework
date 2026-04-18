package dao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import database.JDBCUtil;
import model.Items.Art;
import model.Items.Item;
import model.Items.ItemType;
import model.Items.Vehicle;
import model.User.User;
import model.User.UserSession;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;

public class DAOItems implements DaoInterface<Item> {
    private static final ObjectMapper mapper = new ObjectMapper();
    public static DAOItems getInstance() {
        return new DAOItems();
    }
    User p1 = UserSession.getLoggedInUser();
    @Override
    public int Insert(Item item) {
        Connection con = JDBCUtil.getConnection();
        String sql = "INSERT INTO items (name, startingPrice, sellerId, description, itemType, auctionStartTime, auctionEndTime, imgdata) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (java.sql.PreparedStatement pstmt = con.prepareStatement(sql)) {
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
            pstmt.setBytes(8,item.getImg());

            int rowsAffected = pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
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
    public int Delete(Item item) {
        return 0;
    }

    @Override
    public ArrayList<Item> selectAll(){
        return null;
    }

    public static ArrayList<Item> selectedAll() throws SQLException {
        ArrayList<Item> list = new ArrayList<>();
            String sql = "SELECT * FROM items";
            Connection con = JDBCUtil.getConnection();
            PreparedStatement pstmt = con.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            Item item = new Item() {
                @Override
                public String printInfo() {
                    return "";
                }
            };
            // Lấy dữ liệu cơ bản
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

            // Xử lý dữ liệu ảnh nhị phân (BLOB)
            byte[] imgBytes = rs.getBytes("imgdata");
            // Lưu ý: Bạn cần thêm biến byte[] imgData vào class Item để chứa cái này
            item.setImg(imgBytes);

            list.add(item);}


            return list;
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
