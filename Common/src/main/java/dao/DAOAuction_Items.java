package dao;

import com.google.gson.Gson;
import database.JDBCUtil;
import model.Items.Item;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DAOAuction_Items{
    private static final DAOAuction_Items INSTANCE = new DAOAuction_Items();

    public static DAOAuction_Items getInstance() {
        return INSTANCE;
    }

    private final Gson gson = GsonUtils.createGson();  // Dùng custom Gson với TypeAdapter cho Instant
    /**
     * Precondition: auction và item1 mô tả item đấu giá mới; item1.databaseId đã được
     * DAOItems.Insert() gán sau khi insert items.
     * Postcondition: auction_items có thêm dòng mới với status OPEN, bidHistory rỗng,
     * seller id, leading bidder và current price.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu lỗi.
     */
    public int Insert(Auction auction, Item item) {
        String sql = "INSERT INTO auction_items (id_item, sellerID, status, leadingbider, bidHistory, currentPrice) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setLong(1, item.getDatabaseId());
            pstmt.setString(2, item.getSellerId());

            // Khởi tạo mặc định là OPEN thay vì để NULL
            pstmt.setString(3, gson.toJson(AuctionStatus.OPEN));

            String leadingUsername = auction.getLeadingBidder();
            pstmt.setString(4, leadingUsername);  // Lưu username string trực tiếp, không qua gson
            pstmt.setString(5, gson.toJson(new ArrayList<BidTransaction>()));

            pstmt.setDouble(6, item.getCurrentHighestPrice());

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
    public int Update(Connection con, Auction auction, int itemId, String bidderId, Double price) throws SQLException {
        String sql = "UPDATE auction_items SET currentPrice = ?, leadingbider = ?, bidHistory = ? WHERE id_item = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, price);
            pstmt.setString(2, bidderId);
            pstmt.setString(3, gson.toJson(auction.getBidHistory()));
            pstmt.setLong(4, itemId);
            return pstmt.executeUpdate();
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


            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Auction result = new Auction();
                    result.setItemId(rs.getLong("id_item"));
                    result.setItem(item);
                    item.setCurrentHighestPrice(rs.getDouble("currentPrice"));

                    String leadingUsername = rs.getString("leadingbider");
                    if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                        result.setLeadingBidder(leadingUsername);
                    }

                    String statusJson = rs.getString("status");
                    if (statusJson != null) {
                        try {
                            result.setStatus(gson.fromJson(statusJson, AuctionStatus.class));
                        } catch (Exception e) {
                            result.setStatus(AuctionStatus.valueOf(statusJson.replace("\"", "")));
                        }
                    }

                    String historyJson = rs.getString("bidHistory");
                    if (historyJson != null && !historyJson.isEmpty()) {
                        try {
                            java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                            List<BidTransaction> history = gson.fromJson(historyJson, listType);
                            result.setBidHistory(history);
                        } catch (Exception e) {
                            result.setBidHistory(new ArrayList<>());
                        }
                    }

                    return result;
                }
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
    public void Update_Status(Auction auction, Item item1, AuctionStatus status) {
        // 1. SQL: Cập nhật status trong auction_items table
        String sql = "UPDATE auction_items SET status = ? WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Dùng gson.toJson() để serialized status thành JSON
            pstmt.setString(1, gson.toJson(status));
            pstmt.setLong(2, item1.getDatabaseId());

            // Thực thi
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) { auction.setStatus(status); }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    /**
     * Precondition: item1.databaseId và sellerId đã có dữ liệu.
     * Postcondition: Insert một dòng auction_items tối thiểu với currentPrice.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu SQLException.
     * NOTE: Overload Insert(Auction, Item) là luồng đầy đủ hơn mà UserService.creater_item() dùng.
     */
    public int Insert(Item item) {
        String sql = "INSERT INTO auction_items (id_item, sellerID, currentPrice) VALUES (?, ?, ?)";

        // KHAI BÁO CẢ HAI TRONG TRY: con sẽ tự đóng, pstmt sẽ tự đóng.
        // Không cần dùng biến con ở ngoài, không cần finally.
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setLong(1, item.getDatabaseId());
            pstmt.setString(2, item.getSellerId());
            pstmt.setDouble(3, item.getCurrentHighestPrice());

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

        // ✅ Đã cấu hình chính xác theo các cột thực tế: auctionStartTime, auctionEndTime, imgdata
        String sql = "SELECT " +
                "    a.id_item, a.status, a.currentPrice, a.leadingbider, a.bidHistory, " +
                "    i.name AS item_name, " +
                "    i.auctionStartTime AS item_start, " +
                "    i.auctionEndTime AS item_end, " +
                "    i.imgdata AS item_img " +
                "FROM auction_items a " +
                "LEFT JOIN items i ON a.id_item = i.my_row_id";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Auction auction = new Auction();

                // 1. Đồng bộ ID phiên đấu giá
                auction.setItemId(rs.getLong("id_item"));

                // 2. Khởi tạo và nạp đầy đủ thuộc tính cho thực thể Item để nuôi UI AssetCard
                model.Items.Item item = new model.Items.Item();
                item.setDatabaseId(rs.getInt("id_item"));
                item.setName(rs.getString("item_name"));
                item.setCurrentHighestPrice(rs.getDouble("currentPrice"));

                // Lấy link/tên ảnh từ cột imgdata
                item.setImg(rs.getString("item_img"));

                // ⚡ SỬA LỖI CHÍ MẠNG: Đọc dữ liệu mốc thời gian an toàn từ DB
                Timestamp startTimestamp = rs.getTimestamp("item_start");
                Timestamp endTimestamp = rs.getTimestamp("item_end");

                if (startTimestamp != null) {
                    item.setAuctionStartTime(startTimestamp.toInstant());
                }
                if (endTimestamp != null) {
                    item.setAuctionEndTime(endTimestamp.toInstant());
                }

                // Gán đối tượng Item hoàn chỉnh vào Auction
                auction.setItem(item);

                // 3. Giải mã trạng thái phiên (status)
                String statusJson = rs.getString("status");
                if (statusJson != null) {
                    try {
                        auction.setStatus(gson.fromJson(statusJson, AuctionStatus.class));
                    } catch (Exception e) {
                        auction.setStatus(AuctionStatus.valueOf(statusJson.replace("\"", "")));
                    }
                }

                // 4. Giải mã tài khoản đang dẫn đầu (leadingbider)
                String leadingUsername = rs.getString("leadingbider");
                if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
                    auction.setLeadingBidder(leadingUsername);
                }

                // 5. Giải mã lịch sử các lượt đặt giá (bidHistory)
                String historyJson = rs.getString("bidHistory");
                if (historyJson != null && !historyJson.isEmpty()) {
                    try {
                        java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                        List<BidTransaction> history = gson.fromJson(historyJson, listType);
                        auction.setBidHistory(history);
                    } catch (Exception e) {
                        System.err.println("⚠️ Lỗi deserialize bidHistory trong selectAll: " + e.getMessage());
                        auction.setBidHistory(new ArrayList<>());
                    }
                }

                list.add(auction);
            }
        } catch (SQLException e) {
            System.err.println("Lỗi nghiêm trọng tại selectAll: " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }
    // ✅ SELECT bình thường
    public Auction selectByItemId(Connection con, Item item) throws SQLException {
        String sql = "SELECT * FROM auction_items WHERE id_item = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, item.getDatabaseId());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    // ✅ TRUYỀN THÊM biến item vào đây
                    return mapResultSetToAuction(rs, item);
                }
            }
        }
        return null;
    }
    private Auction mapResultSetToAuction(ResultSet rs, Item item) throws SQLException {
        Auction auction = new Auction();

        long idItem = rs.getLong("id_item");
        auction.setItemId(idItem);

        // ✅ SỬA TẠI ĐÂY: Cập nhật giá mới nhất từ bảng đấu giá vào item có sẵn
        item.setCurrentHighestPrice(rs.getDouble("currentPrice"));

        // Gán object item đã có sẵn từ ngoài vào auction
        auction.setItem(item);

        // --- Các đoạn dưới giữ nguyên ---
        String statusJson = rs.getString("status");
        if (statusJson != null) {
            try {
                auction.setStatus(gson.fromJson(statusJson, AuctionStatus.class));
            } catch (Exception e) {
                auction.setStatus(AuctionStatus.valueOf(statusJson.replace("\"", "")));
            }
        }

        String leadingUsername = rs.getString("leadingbider");
        if (leadingUsername != null && !leadingUsername.trim().isEmpty() && !"null".equals(leadingUsername)) {
            auction.setLeadingBidder(leadingUsername);
        }

        String historyJson = rs.getString("bidHistory");
        if (historyJson != null && !historyJson.isEmpty()) {
            try {
                java.lang.reflect.Type listType = new com.google.gson.reflect.TypeToken<ArrayList<BidTransaction>>(){}.getType();
                List<BidTransaction> history = gson.fromJson(historyJson, listType);
                auction.setBidHistory(history);
            } catch (Exception e) {
                auction.setBidHistory(new ArrayList<>());
            }
        }

        return auction;
    }
    public int Delete(Item item) {
        String sql = "DELETE FROM auction_items WHERE id_item = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, item.getDatabaseId());
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
    /**
     * Thực hiện cập nhật lại giá hiện tại (currentPrice) trong bảng đấu giá
     * dựa theo giá hiện tại mới của Item (khi phiên chưa bắt đầu).
     */
    public int updatePriceByItemIdWhenEditItem(Item item) {
        String sql = "UPDATE auction_items SET currentPrice = ? WHERE id_item = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Vì chưa đấu giá, currentHighestPrice của đối tượng item chính là startingPrice mới
            pstmt.setDouble(1, item.getCurrentHighestPrice());
            pstmt.setLong(2, item.getDatabaseId());

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi tại updatePriceByItemId: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }
}
