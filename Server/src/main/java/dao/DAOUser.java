package dao;

import database.JDBCUtil;
import model.Items.Item;
import model.DepositTransaction;
import model.User.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// DAO user.
public class DAOUser implements DaoInterface<User> {
    private final Gson gson = GsonUtils.createGson();

    public static DAOUser getInstance() {
        return new DAOUser();
    }

    // Ánh xạ dữ liệu user từ ResultSet.
    private User mapRowToUser(ResultSet rs) throws SQLException {
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

    // Thêm user.
    @Override
    public int Insert(User user) throws SQLException {
        String sql = "INSERT INTO khach (username, password, name, email, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getName());
            pstmt.setString(4, user.getAddress());
            pstmt.setString(5, user.getRole_toString());

            return pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi SQL khi insert user: " + e.getMessage());
            throw e;
        }
    }

    // Cập nhật user.
    public void Update(User user) {
        try (Connection con = JDBCUtil.getConnection()) {
            Update(con, user);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Cập nhật user trong transaction.
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

    // Xóa user.
    @Override
    public int Delete(User user) {
        String sql = "DELETE FROM khach WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstm = con.prepareStatement(sql)) {

            pstm.setString(1, user.getUsername());
            return pstm.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Lỗi SQL khi xóa user [" + user.getUsername() + "]: " + e.getMessage());
            return 0;
        }
    }

    // Cập nhật số dư.
    public int UpdateBalance(String username, double newBalance) {
        try (Connection con = JDBCUtil.getConnection()) {
            return UpdateBalance(con, username, newBalance);
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi cập nhật số dư cho user " + username + ": " + e.getMessage(), e);
        }
    }

    // Cập nhật số dư trong transaction.
    public int UpdateBalance(Connection con, String username, double newBalance) throws SQLException {
        String sql = "UPDATE khach SET balance = ? WHERE username = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setDouble(1, newBalance);
            pstmt.setString(2, username);
            return pstmt.executeUpdate();
        }
    }

    // Cập nhật số dư có kiểm tra điều kiện.
    public int UpdateBalanceWithCondition(Connection con, String username, double newBalance, double checkAmount) throws SQLException {
        String sql = "UPDATE khach SET balance = ? WHERE username = ? AND balance >= ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDouble(1, newBalance);
            ps.setString(2, username);
            ps.setDouble(3, checkAmount);
            return ps.executeUpdate();
        }
    }

    // Lấy toàn bộ user.
    @Override
    public ArrayList<User> selectAll()  {
        ArrayList<User> ketQua = new ArrayList<>();
        String sql = "SELECT * FROM khach";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                User user = mapRowToUser(rs);
                if (user != null) ketQua.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ketQua;
    }

    // Lấy trạng thái user.
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

    // Kiểm tra username tồn tại.
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

    // Đăng nhập bằng username và password.
    public User selectByUsername(String username, String password) {
        String sql = "SELECT * FROM khach WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    User user = mapRowToUser(rs);
                    if (user != null && user.getPassword().equals(password)) {
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn User: " + e.getMessage());
        }
        return null;
    }

    // Lấy user theo username.
    public User selectByUsernameOnly(String username) {
        try (Connection con = JDBCUtil.getConnection()) {
            return selectByUsernameOnly(con, username);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Lấy user bằng connection có sẵn.
    public User selectByUsernameOnly(Connection con, String username) throws SQLException {
        String sql = "SELECT * FROM khach WHERE username = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRowToUser(rs);
                }
            }
        }
        return null;
    }

    // Cập nhật lịch sử nạp tiền.
    public int UpdateDepositHistory(String username, List<DepositTransaction> history) {
        String sql = "UPDATE khach SET DepositHistory = ? WHERE username = ?";
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, gson.toJson(history));
            pstmt.setString(2, username);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // Lấy các yêu cầu nạp tiền chờ duyệt.
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

    // Stub interface không dùng.
    @Override
    public int Update(Connection con, Item item) throws SQLException { return 0; }

    // Stub interface không dùng.
    @Override
    public ArrayList<User> moreSelectByCondition(String condition) { return null; }
}
