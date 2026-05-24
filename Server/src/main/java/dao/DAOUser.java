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

public class DAOUser implements DaoInterface<User> {
    private final Gson gson = GsonUtils.createGson();

    public static DAOUser getInstance() {
        return new DAOUser();
    }

    /**
     * Precondition: user có username, password, name, address/email và role.
     * Postcondition: Insert một dòng vào bảng khach nếu SQL chạy thành công.
     * Method hiện luôn trả 0, không phản ánh số dòng bị ảnh hưởng.
     * NOTE: Method đang nối chuỗi SQL trực tiếp nên có rủi ro SQL injection.
     */
    @Override
    public int Insert(User user) throws SQLException {
        // Chuyển sang PreparedStatement để chống SQL Injection
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
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public int Delete(User user) {
        return 0;
    }
    public int UpdateBalance(String username, double newBalance) {
        // Câu lệnh SQL sử dụng dấu '?' làm tham số (Placeholder)
        String sql = "UPDATE khach SET balance = ? WHERE username = ?";
        int ketQua;

        // Đưa cả Connection và PreparedStatement vào try-with-resources để tự động đóng khi dùng xong
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            // Set giá trị cho các dấu '?' theo thứ tự (1, 2, 3...)
            pstmt.setDouble(1, newBalance);
            pstmt.setString(2, username);

            // Thực thi câu lệnh (không truyền chuỗi sql vào executeUpdate nữa)
            ketQua = pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi cập nhật số dư: " + e.getMessage(), e);
        }

        return ketQua;
    }


    @Override
    public ArrayList<User> selectAll()  {
        return null;
    }

    @Override
    public int Update(Connection con, Item item) throws SQLException {
        return 0;
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

    /**
     * Precondition: username xác định một dòng khach.
     * Postcondition: Method trả về khach.status của username, hoặc null nếu không tìm thấy/lỗi.
     */
    public String Get_Status(String username) {
        String sql = "SELECT status FROM khach WHERE username = ?";
        // Bỏ JDBCUtil.closeConnection(con) vì try-with-resources đã làm rồi
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

    /**
     * Precondition: username là tên đăng nhập cần kiểm tra.
     * Postcondition: Method trả true nếu tồn tại dòng khach với username đó; ngược lại trả false.
     */
    public static boolean selectByUsername(String username) {
        String sql = "SELECT username FROM khach WHERE username = ?";

        // Try-with-resources đảm bảo đóng NGAY LẬP TỨC sau khi hàm kết thúc
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // Trả về true nếu tìm thấy, false nếu không
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Precondition: username và password được cung cấp từ luồng login.
     * Postcondition: Method trả về Seller, Bidder hoặc Admin nếu username tồn tại và password khớp;
     * ngược lại trả null.
     * NOTE: Mật khẩu đang được so sánh dạng plain text.
     */
    public User selectByUsername(String username, String password) {
        // 1. Dùng PreparedStatement để chống SQL Injection (rất quan trọng)
        String sql = "SELECT * FROM khach WHERE username = ?";

        // 2. Try-with-resources: Tự động đóng mọi thứ theo đúng thứ tự
        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String dbPassword = rs.getString("password");
                    String role = rs.getString("role");
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    double balance = rs.getDouble("balance");
                    String depositJson = rs.getString("DepositHistory");

                    User user = null;
                    // Trả về đúng đối tượng theo Role
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

                    // 3. Kiểm tra mật khẩu (Nên dùng equals để so sánh String)
                    if (user != null && dbPassword.equals(password)) {
                        return user;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn User: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Không tìm thấy hoặc sai mật khẩu
    }

    /**
     * Precondition: username là tên đăng nhập cần load.
     * Postcondition: Method trả về subclass User theo username mà không kiểm tra password,
     * hoặc null nếu user không tồn tại.
     */
    public User selectByUsernameOnly(String username) {
        // Dùng PreparedStatement để chống SQL Injection
        String sql = "SELECT * FROM khach WHERE username = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String dbPassword = rs.getString("password");
                    String role = rs.getString("role");
                    String name = rs.getString("name");
                    String email = rs.getString("email");
                    double balance = rs.getDouble("balance");
                    String depositJson = rs.getString("DepositHistory");

                    User user = null;
                    // Trả về đúng đối tượng theo Role mà không kiểm tra mật khẩu
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
        } catch (SQLException e) { e.printStackTrace();}
        return null;
    }

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
}

