package database;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/* Tạo kết nối với database bằng JDBC */
public class JDBCUtil {

    private static final Properties props = new Properties();

    static {
        try (InputStream input = JDBCUtil.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input == null) {
                System.err.println("Không tìm thấy file server.properties");
            } else {
                props.load(input);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private JDBCUtil(){}; // Private constructor

    // 2. Phương thức lấy kết nối (Trả về kết nối MỚI mỗi lần gọi)
    public static Connection getConnection() {
        Connection con = null;
        try {
            DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());

            String host = props.getProperty("db.host", "localhost");
            String database = props.getProperty("db.name", "quan_ly_dau_gia");
            String user = props.getProperty("db.user", "root");
            String pass = props.getProperty("db.pass", "");

            // Chuỗi URL với cấu hình Azure
            String url = "jdbc:mysql://" + host + ":3306/" + database +
                    "?useSSL=true&requireSSL=false&serverTimezone=UTC";

            // 3. TẠO VÀ TRẢ VỀ KẾT NỐI MỚI HOÀN TOÀN
            con = DriverManager.getConnection(url, user, pass);

        } catch (SQLException e) {
            System.err.println("Lỗi tạo kết nối DB: " + e.getMessage());
            e.printStackTrace();
        }
        return con;
    }

    // 4. Giữ lại hàm này đề phòng bạn còn dùng ở các file DAO cũ chưa kịp sửa sang try-with-resources
    public static void closeConnection(Connection c) {
        try {
            if (c != null && !c.isClosed()) {
                c.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}