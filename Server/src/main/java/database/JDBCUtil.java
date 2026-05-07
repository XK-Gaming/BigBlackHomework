package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Utility tạo kết nối JDBC sử dụng HikariCP Connection Pool.
 * Giúp tối ưu hóa tốc độ kết nối và quản lý tài nguyên hiệu quả trên Azure.
 */
public class JDBCUtil {

    private static final Properties props = new Properties();
    private static HikariDataSource dataSource;

    static {
        // 1. Load cấu hình từ server.properties
        try (InputStream input = JDBCUtil.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input == null) {
                System.err.println("Không tìm thấy file server.properties");
            } else {
                props.load(input);
            }

            // 2. Cấu hình HikariCP Connection Pool
            HikariConfig config = new HikariConfig();

            String host = props.getProperty("db.host", "localhost");
            String database = props.getProperty("db.name", "quan_ly_dau_gia");
            String user = props.getProperty("db.user", "root");
            String pass = props.getProperty("db.pass", "");

            // Chuỗi URL tối ưu cho Azure MySQL
            String url = "jdbc:mysql://" + host + ":3306/" + database +
                    "?useSSL=true&requireSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            // --- TỐI ƯU HÓA HIỆU NĂNG ---
            config.setMaximumPoolSize(10); // Giữ sẵn 10 kết nối luôn mở
            config.setMinimumIdle(2);      // Luôn duy trì ít nhất 2 kết nối rảnh
            config.setConnectionTimeout(20000); // Chờ tối đa 20s để lấy kết nối
            config.setIdleTimeout(300000);      // 5 phút không dùng thì đóng bớt kết nối rảnh

            // Cache các câu lệnh SQL để chạy nhanh hơn ở những lần sau
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);
            System.out.println("[DB] Hikari Connection Pool đã khởi tạo thành công!");

        } catch (Exception e) {
            System.err.println("[DB] Lỗi khởi tạo Connection Pool: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private JDBCUtil() {} // Ngăn khởi tạo class

    /**
     * Lấy một kết nối từ Pool (Tốc độ cực nhanh vì đã kết nối sẵn tới Azure).
     * @return Connection từ pool hoặc ném ra SQLException nếu lỗi.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource chưa được khởi tạo!");
        }
        return dataSource.getConnection();
    }

    /**
     * Không cần thiết phải đóng kết nối thủ công như cũ nếu dùng try-with-resources.
     * Tuy nhiên giữ lại hàm này để tương thích với code cũ của bạn.
     * Trong Connection Pool, close() nghĩa là TRẢ kết nối về pool, không phải ngắt kết nối.
     */
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
