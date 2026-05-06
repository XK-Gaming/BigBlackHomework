package dao;

import database.JDBCUtil;
import model.Items.Item;
import model.User.*;
import model.auction.Auction;

import java.sql.*;
import java.util.ArrayList;

/**
 * DAO thao tác bảng khach.
 *
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
        Connection con = JDBCUtil.getConnection();

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
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    @Override
    public int Insert(Auction auction, Item item1) {
        return 0;
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Không thay đổi state. Method trả 0.
     */
    @Override
    public int Insert(Item item) {
        return 0;
    }

    /**
     * Precondition: Dự kiến nhận user chứa các field đã cập nhật.
     * Postcondition: Phiên bản hiện tại không thay đổi state. Method trả 0.
     * NOTE: UserService.updateUser() và changePassword() đang phụ thuộc method này.
     */
    @Override
    public int Update(User user) {
        return 0;
    }

    /**
     * Precondition: username xác định một dòng khach tồn tại, idItem là item user đang xem.
     * Postcondition: Cập nhật khach.status cho username.
     * Method trả về số dòng bị ảnh hưởng, hoặc 0 nếu SQLException.
     */
    public int Update_Status(String username, String idItem )
    {String sql = "UPDATE khach SET status = ? WHERE username = ?";

        try (Connection con = JDBCUtil.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setString(1, idItem);
            pstmt.setString(2, username);

            // Thực thi lệnh và trả về số dòng bị ảnh hưởng (thường là 1 nếu thành công)
            return pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0; // Trả về 0 nếu có lỗi xảy ra
        }
    }
    /**
     * Precondition: username xác định một dòng khach.
     * Postcondition: Method trả về khach.status của username, hoặc null nếu không tìm thấy/lỗi.
     */
    public String Get_Status(String username) {
        Connection con = JDBCUtil.getConnection();
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
        }
        finally {
            JDBCUtil.closeConnection(con);}
        return null;
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
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Method trả null.
     */
    @Override
    public User selectByUsername(User user) {
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

                    // 3. Kiểm tra mật khẩu (Nên dùng equals để so sánh String)
                    if (dbPassword.equals(password)) {
                        // Trả về đúng đối tượng theo Role
                        if ("Người bán".equals(role)) {
                            return new Seller(username, dbPassword, name, email);
                        } else if ("Người đấu giá".equals(role)) {
                            return new Bidder(username, dbPassword, name, email);
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

                    // Trả về đúng đối tượng theo Role mà không kiểm tra mật khẩu
                    if ("Người bán".equals(role)) {
                        return new Seller(username, dbPassword, name, email);
                    } else if ("Người đấu giá".equals(role)) {
                        return new Bidder(username, dbPassword, name, email);
                    } else if ("Admin".equals(role)) {
                        return new Admin(username, dbPassword, name, email);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Lỗi truy vấn User: " + e.getMessage());
            e.printStackTrace();
        }
        return null; // Không tìm thấy
    }

    /**
     * Precondition: Không được implement cho DAOUser.
     * Postcondition: Method trả null.
     */
    @Override
    public ArrayList selectByCondition(String condition) {
        return null;
    }
}
