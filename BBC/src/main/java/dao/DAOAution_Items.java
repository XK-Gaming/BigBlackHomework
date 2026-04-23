package dao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import database.JDBCUtil;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.Bidder;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 *Lây dữ liệu tương tác với database
 */

public class DAOAution_Items {
    public  static ObjectMapper mapper = new ObjectMapper();
    public static DAOAution_Items getInstance() {
        return new DAOAution_Items();
    }
    public int Insert(Auction auction, Item item1) {
        Connection con = JDBCUtil.getConnection();
        String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingBidder, currentPrice) VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            // 1. id_item
            pstmt.setInt(1, item1.getId_Item());

            // 2. sellerID
            pstmt.setString(2, item1.getSellerId());

            // Biến đối tượng thành chuỗi JSON
            String jsonString = mapper.writeValueAsString(AuctionStatus.OPEN);
            pstmt.setString(3, jsonString);

            // 4. LeadingBidder (Bidder)
            String jsonString4 = mapper.writeValueAsString(auction.getLeadingBidder());
            pstmt.setString(4, jsonString4);

            // 5. currentPrice (String)
            pstmt.setDouble(5, item1.getCurrentHighestPrice());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Luôn đóng kết nối trong finally hoặc dùng try-with-resources
            JDBCUtil.closeConnection(con);
        }
        return 0;
    }
    public int Insert(Item item1) {
        Connection con = JDBCUtil.getConnection();
        String sql = "INSERT INTO auction_items (id_item, sellerID, currentPrice) VALUES (?, ?, ?)";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            // 1. id_item
            pstmt.setInt(1, item1.getId_Item());

            // 2. sellerID
            pstmt.setString(2, item1.getSellerId());

            // 5. currentPrice (String)
            pstmt.setDouble(3, item1.getCurrentHighestPrice());

            pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // Luôn đóng kết nối trong finally hoặc dùng try-with-resources
            JDBCUtil.closeConnection(con);
        }
        return 0;
    }


    public int Update(Auction auction, Item item1, Bidder leading) {
        // Câu lệnh SQL cập nhật dựa trên ID món hàng (item_id)
        String sql = "UPDATE Auction_items SET currentPrice = ?, leadingbider = ?, bidHistory = ? WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // 1. Set giá tiền mới (người vừa trả cao nhất)
            pstmt.setDouble(1, item1.getCurrentHighestPrice());

            String jsonString2 = mapper.writeValueAsString(leading);
            pstmt.setString(2, jsonString2);

            String jsonString3 = mapper.writeValueAsString(auction.getBidHistory());
            pstmt.setString(3, jsonString3);

            // 4. Xác định cập nhật cho món hàng nào dựa trên ID (BIGINT)
            pstmt.setLong(4, item1.getId_Item());

            // Thực thi lệnh và trả về số dòng bị ảnh hưởng (thường là 1 nếu thành công)
            return pstmt.executeUpdate();

        } catch (SQLException | JsonProcessingException e) {
            e.printStackTrace();
            return 0; // Trả về 0 nếu có lỗi xảy ra
        }
    }

    public int Delete(Auction auction) {
        return 0;
    }

    public ArrayList<Auction> selectAll(){
        return null;
    }

    public Auction selectByUsername(Auction auction) {
        return null;
    }

    public Auction selectByItemId(Item item) {
        Auction result;

        // Lấy thông tin từ bảng Auction_items dựa trên item_id (khóa chính)
        String sql = "SELECT * FROM Auction_items WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Truyền ID từ đối tượng auction vào câu lệnh SQL
            pstmt.setLong(1, item.getId_Item() );

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Khởi tạo đối tượng mới để chứa dữ liệu trả về
                result = new Auction();
                result.setItemId(rs.getLong("id_item"));
                item.setCurrentHighestPrice(rs.getInt("currentPrice"));
                // 1. Lấy chuỗi JSON từ ResultSet
                String jsonString = rs.getString("leadingbider");


                // 1. Khởi tạo đối tượng Gson
                Gson gson = new Gson();

                // 2. Dùng hàm fromJson để "đọc" chuỗi thành đối tượng Bidder
                // Tương tự dòng: BagOfPrimitives obj2 = gson.fromJson(json, BagOfPrimitives.class);
                Bidder leadingBidder = gson.fromJson(jsonString, Bidder.class);
                result.setLeadingBidder(leadingBidder);

                /*String jsonString2 = rs.getString("status");


                // 1. Khởi tạo đối tượng Gson
                model.auction.AuctionStatus obj3 = gson.fromJson(jsonString2, AuctionStatus.class);

                result.setStatus(obj3);
                String jsonString3 = rs.getString("bidHistory");


                // 2. Dùng hàm fromJson để "đọc" chuỗi thành đối tượng Bidder

                List<BidTransaction> obj4 = gson.fromJson(jsonString3, List.class);
                result.setbidHistory(obj4);*/
                return result;
            }
            }
        catch (SQLException e) {
            e.printStackTrace();
        }
        return null;

    }



}
