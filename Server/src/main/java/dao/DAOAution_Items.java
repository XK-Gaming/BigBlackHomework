package dao;

import com.google.gson.Gson;
import database.JDBCUtil;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.Bidder;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;
import utils.GsonUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 *Lây dữ liệu tương tác với database
 */

/**
 * DAO thao tác bảng auction_items.
 *
 * Trách nhiệm class: tạo dòng auction, cập nhật bid/trạng thái, serialize và deserialize
 * status cùng bid history.
 */
public class DAOAution_Items{
    /**
     * Precondition: Không có.
     * Postcondition: Method trả về một instance DAOAution_Items mới.
     */
    public static DAOAution_Items getInstance() {return new DAOAution_Items();}
    /** Gson đã cấu hình TypeAdapter cho Instant và BidTransaction trong cột JSON. */
    private Gson gson = GsonUtils.createGson();  // Dùng custom Gson với TypeAdapter cho Instant
        // Hàm Insert này đảm bảo không bao giờ bị NULL status khi tạo mới
        /**
         * Precondition: auction và item1 mô tả item đấu giá mới; item1.databaseId đã được
         * DAOItems.Insert() gán sau khi insert items.
         * Postcondition: auction_items có thêm dòng mới với status OPEN, bidHistory rỗng,
         * seller id, leading bidder và current price.
         * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu lỗi.
         */
        public int Insert(Auction auction, Item item1) {
            String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingbider, bidHistory, currentPrice) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection con = JDBCUtil.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setInt(1, item1.getDatabaseId());
                pstmt.setString(2, item1.getSellerId());

                // Khởi tạo mặc định là OPEN thay vì để NULL
                pstmt.setString(3, gson.toJson(AuctionStatus.OPEN));

                String leadingUsername = auction.getLeadingBidder();
                pstmt.setString(4, leadingUsername);  // Lưu username string trực tiếp, không qua gson
                pstmt.setString(5, gson.toJson(new ArrayList<BidTransaction>()));

                pstmt.setDouble(6, item1.getCurrentHighestPrice());

                return pstmt.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        /**
         * Precondition: itemId xác định một dòng auction_items, UsernameLeadingBiddder là username
         * đang dẫn đầu, CurrentPrice là mức giá vừa được chấp nhận.
         * Postcondition: Cập nhật currentPrice, leadingbider và bidHistory cho item.
         * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu validate/database lỗi.
         * NOTE: leading bidder rỗng sẽ bị từ chối trước khi chạy SQL.
         */
        public int Update(Auction auction, int itemId, String UsernameLeadingBiddder, Double CurrentPrice) {
            if (UsernameLeadingBiddder == null || UsernameLeadingBiddder.trim().isEmpty()) {
                return 0;
            }

            String sql = "UPDATE auction_items SET currentPrice = ?, leadingbider = ?, bidHistory = ? WHERE id_item = ?";

            try (Connection con = JDBCUtil.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setDouble(1, CurrentPrice);
                pstmt.setString(2, UsernameLeadingBiddder);

                String bidHistoryJson = gson.toJson(auction.getBidHistory());
                pstmt.setString(3, bidHistoryJson);
                pstmt.setLong(4, itemId);

                int rowsAffected = pstmt.executeUpdate();
                return rowsAffected;

            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        }

        /**
         * Precondition: item khác null và item.databaseId xác định dòng items/auction_items.
         * Postcondition: Method trả về Auction đã nạp item, status, leadingBidder và bidHistory.
         * Trả null nếu không có dòng auction.
         * NOTE: Nếu parse JSON bidHistory lỗi thì gán lịch sử rỗng.
         */
        public Auction selectByItemId(Item item) {
            String sql = "SELECT * FROM auction_items WHERE id_item = ?";
            try (Connection con = JDBCUtil.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setLong(1, item.getDatabaseId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    Auction result = new Auction();
                    result.setItemId(rs.getLong("id_item"));
                    result.setItem(item);
                    item.setCurrentHighestPrice(rs.getDouble("currentPrice"));
                    String leadingUsername = rs.getString("leadingbider");
                    String historyJson = rs.getString("bidHistory");
                    if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                        result.setLeadingBidder(leadingUsername);}
                    // Đọc Status
                    String statusJson = rs.getString("status");
                    if (statusJson != null) {
                        result.setStatus(gson.fromJson(statusJson, model.auction.AuctionStatus.class));}
                    // Đọc Bid History (Quan trọng: Phải gán lại cho Auction)
                    if (historyJson != null && !historyJson.isEmpty()) {
                        try {
                            // Dùng TypeToken để deserialize đúng kiểu List<BidTransaction>
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                            List<BidTransaction> history = gson.fromJson(historyJson, listType);
                            result.setbidHistory(history);
                        } catch (Exception e) {
                            result.setbidHistory(new ArrayList<>()); // Gán danh sách rỗng nếu có lỗi
                        }
                    }

                    return result;
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }
    /**
     * Precondition: item1.databaseId xác định dòng auction_items và status là trạng thái đích.
     * Postcondition: Cập nhật auction_items.status và cập nhật cả status trong object auction
     * nếu dòng database tồn tại.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu SQLException.
     */
    public int Update_Status(Auction auction, Item item1, AuctionStatus status) {
        // 1. SQL: Cập nhật status trong auction_items table
        String sql = "UPDATE auction_items SET status = ? WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Dùng gson.toJson() để serialized status thành JSON
            pstmt.setString(1, gson.toJson(status));
            pstmt.setLong(2, item1.getDatabaseId());

            // Thực thi
            int rowsAffected = pstmt.executeUpdate();

            return rowsAffected;

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * Precondition: item1.databaseId và sellerId đã có dữ liệu.
     * Postcondition: Insert một dòng auction_items tối thiểu với currentPrice.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu SQLException.
     * NOTE: Overload Insert(Auction, Item) là luồng đầy đủ hơn mà UserService.creater_item() dùng.
     */
    public int Insert(Item item1) {
        String sql = "INSERT INTO auction_items (id_item, sellerID, currentPrice) VALUES (?, ?, ?)";

        // KHAI BÁO CẢ HAI TRONG TRY: con sẽ tự đóng, pstmt sẽ tự đóng.
        // Không cần dùng biến con ở ngoài, không cần finally.
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            if (con == null) return 0;

            pstmt.setInt(1, item1.getDatabaseId());
            pstmt.setString(2, item1.getSellerId());
            pstmt.setDouble(3, item1.getCurrentHighestPrice());

            return pstmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Lỗi tại Insert: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
        // Không cần finally ở đây nữa!
    }

    /**
     * Precondition: Có thể tạo kết nối database và bảng auction_items tồn tại.
     * Postcondition: Method trả về toàn bộ dòng auction_items đã map sang Auction object.
     * NOTE: Method này chưa gắn Item object; AuctionEngine sẽ load Item sau bằng itemId.
     */
    public List<Auction> selectAll() {
        List<Auction> list = new ArrayList<>();
        // Câu lệnh lấy tất cả dữ liệu từ bảng
        String sql = "SELECT * FROM auction_items";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Auction auction = new Auction();

                // 1. Lấy các thông tin cơ bản
                auction.setItemId(rs.getLong("id_item"));
                // Lưu ý: Nếu bạn có đối tượng Item bên trong Auction, hãy set nó ở đây
                // auction.setCurrentPrice(rs.getDouble("currentPrice"));

                // 2. Giải mã Status (Enum)
                String statusJson = rs.getString("status");
                if (statusJson != null) {
                    // Nếu bạn lưu bằng name() thì dùng AuctionStatus.valueOf()
                    // Nếu lưu bằng gson.toJson() thì dùng dòng dưới:
                    try {
                        auction.setStatus(gson.fromJson(statusJson, AuctionStatus.class));
                    } catch (Exception e) {
                        // Dự phòng nếu trong DB chỉ là chữ thuần "OPEN" không có ngoặc kép
                        auction.setStatus(AuctionStatus.valueOf(statusJson.replace("\"", "")));
                    }
                }

                // 3. Giải mã Leading Bidder (Username string)
                String leadingUsername = rs.getString("leadingbider");
                if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                    auction.setLeadingBidder(leadingUsername);
                }

                // 4. Giải mã Bid History (List)
                String historyJson = rs.getString("bidHistory");
                if (historyJson != null && !historyJson.isEmpty()) {
                    try {
                        // ✅ Dùng custom Gson với TypeAdapter cho BidTransaction
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                        List<BidTransaction> history = gson.fromJson(historyJson, listType);
                        auction.setbidHistory(history);
                    } catch (Exception e) {
                        System.err.println("⚠️ Lỗi deserialize bidHistory trong selectAll: " + e.getMessage());
                        auction.setbidHistory(new ArrayList<>());
                    }
                }

                // Thêm vào danh sách kết quả
                list.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi khi SelectAll: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }
}
