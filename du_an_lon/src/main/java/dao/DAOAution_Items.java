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
import utils.GsonUtils;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Tạo Data Access Object (Đối tượng Truy cập Dữ liệu).
 *Lây dữ liệu tương tác với database
 */

public class DAOAution_Items {
    public  static ObjectMapper mapper = new ObjectMapper();
    public static DAOAution_Items getInstance() {return new DAOAution_Items();}
    private Gson gson = GsonUtils.createGson();  // Dùng custom Gson với TypeAdapter cho Instant
        // Hàm Insert này đảm bảo không bao giờ bị NULL status khi tạo mới
        public int Insert(Auction auction, Item item1) {
            String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingbider, bidHistory, currentPrice) VALUES (?, ?, ?, ?, ?, ?)";

            try (Connection con = JDBCUtil.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setInt(1, item1.getDatabaseId());
                pstmt.setString(2, item1.getSellerId());

                // Khởi tạo mặc định là OPEN thay vì để NULL
                pstmt.setString(3, gson.toJson(AuctionStatus.OPEN));

                // ✅ FIX: Lưu username trực tiếp (ko qua gson) để consistent với hàm Update()
                String leadingUsername = auction.getLeadingBidder();
                pstmt.setString(4, leadingUsername);  // Lưu username string trực tiếp, không qua gson

                // Khởi tạo lịch sử trống [] thay vì NULL
                pstmt.setString(5, gson.toJson(new ArrayList<BidTransaction>()));

                pstmt.setDouble(6, item1.getCurrentHighestPrice());

                return pstmt.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }

        public int Update(Auction auction, int itemId, String UsernameLeadingBiddder, Double CurrentPrice) {
            // ✅ Cập nhật currentPrice, leading bidder username, và bid history vào auction_items table

            // ✅ Defensive check
            if (UsernameLeadingBiddder == null || UsernameLeadingBiddder.trim().isEmpty()) {
                System.err.println("❌ Update: UsernameLeadingBiddder bị null hoặc rỗng!");
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

                if (rowsAffected > 0) {
                    System.out.println("✅ UPDATE auction_items: item=" + itemId + ", leadingbider=" + UsernameLeadingBiddder + ", bidHistorySize=" + auction.getBidHistory().size());
                } else {
                    System.err.println("❌ UPDATE: Không tìm thấy item ID " + itemId + " trong auction_items");
                }
                return rowsAffected;

            } catch (SQLException e) {
                System.err.println("❌ Lỗi UPDATE auction_items: " + e.getMessage());
                e.printStackTrace();
                return 0;
            }
        }

        public Auction selectByItemId(Item item) {
            String sql = "SELECT * FROM auction_items WHERE id_item = ?";
            try (Connection con = JDBCUtil.getConnection();
                 PreparedStatement pstmt = con.prepareStatement(sql)) {

                pstmt.setLong(1, item.getDatabaseId());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    Auction result = new Auction();
                    result.setItemId(rs.getLong("id_item"));

                    // ✅ FIX: Set item vào Auction để tránh NullPointerException
                    result.setItem(item);

                    // Update giá trực tiếp vào item truyền vào
                    item.setCurrentHighestPrice(rs.getDouble("currentPrice"));

                    // ✅ FIX: Lấy dữ liệu từ DB TRƯỚC khi sử dụng
                    String leadingUsername = rs.getString("leadingbider");
                    String historyJson = rs.getString("bidHistory");

                    // ✅ DEBUG: Log dữ liệu từ DB
                    System.out.println("DEBUG selectByItemId: Raw leadingbider from DB = '" + leadingUsername + "'");
                    System.out.println("DEBUG selectByItemId: Raw bidHistory JSON = " + historyJson);

                    // ✅ FIX: Load Bidder từ username (chỉ lấy username string)
                    if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                        result.setLeadingBidder(leadingUsername);
                        System.out.println("✅ selectByItemId: Set leadingBidder = " + leadingUsername);
                    } else {
                        System.out.println("INFO: selectByItemId - Chưa có người dẫn đầu cho item ID " + item.getDatabaseId() + " (leadingbider='" + leadingUsername + "')");
                    }

                    // Đọc Status
                    String statusJson = rs.getString("status");
                    if (statusJson != null) {
                        result.setStatus(gson.fromJson(statusJson, model.auction.AuctionStatus.class));
                    }

                    // Đọc Bid History (Quan trọng: Phải gán lại cho Auction)
                    if (historyJson != null && !historyJson.isEmpty()) {
                        try {
                            // ✅ Dùng TypeToken để deserialize đúng kiểu List<BidTransaction>
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                            List<BidTransaction> history = gson.fromJson(historyJson, listType);
                            result.setbidHistory(history);
                        } catch (Exception e) {
                            System.err.println("⚠️ Lỗi deserialize bidHistory: " + e.getMessage());
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
    public int Update_Status(Auction auction, Item item1, AuctionStatus status) {
        // 1. SQL: Cập nhật status trong auction_items table
        String sql = "UPDATE auction_items SET status = ? WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // ✅ Dùng gson.toJson() để serialized status thành JSON
            pstmt.setString(1, gson.toJson(status));
            pstmt.setLong(2, item1.getDatabaseId());

            // Thực thi
            int rowsAffected = pstmt.executeUpdate();

            // Cập nhật object in-memory
            if (rowsAffected > 0 && auction != null) {
                auction.setStatus(status);
                System.out.println("✅ Cập nhật status item ID " + item1.getDatabaseId() + " thành " + status);
            } else if (rowsAffected == 0) {
                System.err.println("❌ Không tìm thấy auction_items record cho item ID " + item1.getDatabaseId());
            }

            return rowsAffected;

        } catch (SQLException e) {
            System.err.println("❌ Lỗi khi cập nhật status item ID " + item1.getDatabaseId() + ": " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
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