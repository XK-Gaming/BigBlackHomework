package dao;

import database.JDBCUtil;
import model.Items.Item;
import model.DepositTransaction;
import model.User.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DAOUser implements DaoInterface<User> {
    private final Gson gson = GsonUtils.createGson();

    public static DAOUser getInstance() {
        return new DAOUser();
    }

    @Override
    public int Insert(User user) throws SQLException {
        String sql = "INSERT INTO khach (username, password, name, email, role) VALUES (?, ?, ?, ?, ?)";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getName());
            pstmt.setString(4, user.getAddress()); // Giữ nguyên map field cũ của bạn
            pstmt.setString(5, user.getRole_toString());

            return pstmt.executeUpdate();
        }
    }

    public void Update(User user) {
        try (Connection con = JDBCUtil.getConnection()) {
            Update(con, user);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int Delete(User user) {
        int result = 0;
        String sql = "DELETE FROM khach WHERE username = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setString(1, user.getUsername());
            result = pstm.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Lỗi SQL khi xóa user [" + user.getUsername() + "]: " + e.getMessage());
            return 0;
        }
        return result;
    }

    /**
     * Phương thức cập nhật số dư mặc định (tự tạo Connection độc lập)
     */
    public int UpdateBalance(String username, double newBalance) {
        try (Connection con = JDBCUtil.getConnection()) {
            return UpdateBalance(con, username, newBalance);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi cập nhật số dư: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ ĐÃ ĐỒNG BỘ: Sử dụng Connection từ Service để cập nhật số dư trong Transaction
     */
    public int UpdateBalance(Connection con, String username, double newBalance) throws SQLException {
        String sql = "UPDATE khach SET balance = ? WHERE username = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, newBalance);
            pstmt.setString(2, username);
            return pstmt.executeUpdate();
        }
    }

    /**
     * ✅ THÊM MỚI: Cập nhật động các trường thông tin cá nhân của User
     * Giải quyết lỗi "Cannot resolve method 'updateUserField'" ở UserService
     */
    public boolean updateUserField(String username, String field, String value) {
        String sql = switch (field) {
            case "name" -> "UPDATE khach SET name = ? WHERE username = ?";
            case "phone" -> "UPDATE khach SET phone = ? WHERE username = ?"; // Hãy chắc chắn bảng 'khach' có cột phone
            case "address" -> "UPDATE khach SET email = ? WHERE username = ?"; // Map tạm vào 'email' theo logic cũ của bạn, hoặc đổi thành cột address nếu có
            default -> throw new IllegalArgumentException("Trường cập nhật không hợp lệ: " + field);
        };

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (SQLException | IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ THÊM MỚI: Đổi mật khẩu an toàn kiểm tra mật khẩu cũ trực tiếp dưới DB
     * Giải quyết lỗi "Cannot resolve method 'changePassword'" ở UserService
     */
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        String sql = "UPDATE khach SET password = ? WHERE username = ? AND password = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setString(2, username);
            ps.setString(3, oldPassword);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public ArrayList<User> selectAll()  {
        ArrayList<User> ketQua = new ArrayList<>();
        String sql = "SELECT * FROM khach";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                User user = mapUserFromResultSet(rs);
                if (user != null) ketQua.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ketQua;
    }

    @Override
    public int Update(Connection con, Item item) throws SQLException {
        return 0;
    }

    @Override
    public ArrayList<User> moreSelectByCondition(String condition) {
        return null;
    }

    @Override
    public int Update(Connection con, User user) throws SQLException {
        String sql = "UPDATE khach SET password = ?, name = ?, email = ? WHERE username = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, user.getPassword());
            pstmt.setString(2, user.getName());
            pstmt.setString(3, user.getAddress());
            pstmt.setString(4, user.getUsername());

            return pstmt.executeUpdate();
        }
    }

    public String Get_Status(String username) {
        String sql = "SELECT status FROM khach WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("status");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean selectByUsername(String username) {
        String sql = "SELECT username FROM khach WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public User selectByUsername(String username, String password) {
        String sql = "SELECT * FROM khach WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = mapUserFromResultSet(rs);
                    if (user != null && BCrypt.checkpw(password, user.getPassword())) {
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn User: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public User selectByUsernameOnly(String username) {
        try (Connection con = JDBCUtil.getConnection()) {
            return selectByUsernameOnly(con, username);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public User selectByUsernameOnly(Connection con, String username) throws SQLException {
        String sql = "SELECT * FROM khach WHERE username = ? FOR UPDATE";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapUserFromResultSet(rs);
                }
            }
        }
        return null;
    }

    public int UpdateDepositHistory(String username, List<DepositTransaction> history) {
        try (Connection con = JDBCUtil.getConnection()) {
            return UpdateDepositHistory(con, username, history);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public int UpdateDepositHistory(Connection con, String username, List<DepositTransaction> history) throws SQLException {
        String sql = "UPDATE khach SET DepositHistory = ? WHERE username = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, gson.toJson(history));
            pstmt.setString(2, username);
            return pstmt.executeUpdate();
        }
    }

    public List<DepositTransaction> getAllPendingDeposits() {
        List<DepositTransaction> pending = new ArrayList<>();
        String sql = "SELECT username, DepositHistory FROM khach WHERE DepositHistory IS NOT NULL AND DepositHistory <> ''";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String depositJson = rs.getString("DepositHistory");
                List<DepositTransaction> history = gson.fromJson(depositJson, new TypeToken<ArrayList<DepositTransaction>>(){}.getType());
                if (history != null) {
                    for (DepositTransaction dt : history) {
                        if ("PENDING".equals(dt.getStatus())) {
                            pending.add(dt);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return pending;
    }

    private User mapUserFromResultSet(ResultSet rs) throws SQLException {
        String username = rs.getString("username");
        String dbPassword = rs.getString("password");
        String role = rs.getString("role");
        String name = rs.getString("name");
        String email = rs.getString("email");
        double balance = rs.getDouble("balance");
        String depositJson = rs.getString("DepositHistory");

        User user = null;
        if ("Người bán".equals(role)) {
            user = new Seller(username, dbPassword, name, email, balance);
        } else if ("Người đấu giá".equals(role)) {
            user = new Bidder(username, dbPassword, name, email, balance);
        } else if ("Admin".equals(role)) {
            user = new Admin(username, dbPassword, name, email);
        }

        if (user != null && depositJson != null && !depositJson.isEmpty()) {
            List<DepositTransaction> history = gson.fromJson(depositJson, new TypeToken<ArrayList<DepositTransaction>>(){}.getType());
            user.setDepositHistory(history);
        }
        return user;
    }
}