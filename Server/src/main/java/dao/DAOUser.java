package dao;

import database.JDBCUtil;
import model.Items.Item;
import model.User.*;
import model.auction.Auction;

import java.sql.*;
import java.util.ArrayList;

/**
 * DAO thao tác bảng khach.
 * <p>
 * Trách nhiệm class: tạo user, xác thực user, map role trong database sang subclass User,
 * và đọc/ghi status item user đang xem.
 */
public class DAOUser implements DaoInterface<User> {

    /**
     * Precondition: Không có.
     * Postcondition: Method trả về một instance DAOUser mới.
     */
    public static DAOUser getInstance() {
        return new DAOUser();
    }
    // lấy dữ liệu bằng CreateStatement...

    /**
     * Precondition: user có username, password, name, address/email và role.
     * Postcondition: Insert một dòng vào bảng khach nếu SQL chạy thành công.
     * Method hiện luôn trả 0, không phản ánh số dòng bị ảnh hưởng.
     * NOTE: Method đang nối chuỗi SQL trực tiếp nên có rủi ro SQL injection.
     */
    @Override
    public int Insert(User user) {
        try (Connection con = JDBCUtil.getConnection();) {
            Statement st = null;
            try {
                st = con.createStatement();

                String sql = "INSERT INTO khach (username, password, name, email, role) " +
                        " VALUES('" + user.getUsername() + "', '" + user.getPassword() + "', '" +
                        user.getName() + "', '" + user.getAddress() + "', '" + user.getRole_toString() + "')";
                int ketQua = st.executeUpdate(sql);
                JDBCUtil.closeConnection(con);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
    @Override
    public int Update(User user) {
        try (Connection con = JDBCUtil.getConnection();) {
            Statement st = null;
            try {
                st = con.createStatement();

                String sql = "UPDATE khach SET password = '" + user.getPassword() + "', name = '" +
                        user.getName() + "', email = '" + user.getAddress() + "' WHERE username = '" + user.getUsername() + "'";
                int ketQua = st.executeUpdate(sql);
                JDBCUtil.closeConnection(con);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    public int UpdateBalance(String username, double newBalance) {
        // Câu lệnh SQL sử dụng dấu '?' làm tham số (Placeholder)
        String sql = "UPDATE khach SET balance = ? WHERE username = ?";
        int ketQua = 0;

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

    /**
     * Precondition: username xác định một dòng khach.
     * Postcondition: Method trả về khach.status của username, hoặc null nếu không tìm thấy/lỗi.
     */
    public String Get_Status(String username) {
        try (Connection con = JDBCUtil.getConnection()) {
            String sql = "SELECT status FROM khach WHERE username = ?";
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setString(1, username);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    // Trả về giá trị của cột "status" kiểu int
                    return rs.getString("status");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            } finally {
                JDBCUtil.closeConnection(con);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    @Override
    public int Delete(User user) {
        return 0;
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Method trả null.
     */
    @Override
    public ArrayList selectAll() {
        return null;
    }@Override
    public ArrayList<User> moreSelectByCondition(String condition) {
        return null;
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Method trả null.
     */

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

                    // 3. Kiểm tra mật khẩu (Nên dùng equals để so sánh String)
                    if (dbPassword.equals(password)) {
                        // Trả về đúng đối tượng theo Role
                        if ("Người bán".equals(role)) {
                            return new Seller(username, dbPassword, name, email, balance);
                        } else if ("Người đấu giá".equals(role)) {
                            return new Bidder(username, dbPassword, name, email, balance);
                        } else if ("Admin".equals(role)) {
                            return new Admin(username, dbPassword, name, email);
                        }
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

                    // Trả về đúng đối tượng theo Role mà không kiểm tra mật khẩu
                    if ("Người bán".equals(role)) {
                        return new Seller(username, dbPassword, name, email, balance);
                    } else if ("Người đấu giá".equals(role)) {
                        return new Bidder(username, dbPassword, name, email, balance);
                    } else if ("Admin".equals(role)) {
                        return new Admin(username, dbPassword, name, email);
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace();}
        return null; // Không tìm thấy
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Method trả null.
     */

}