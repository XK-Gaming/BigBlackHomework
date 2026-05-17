package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/* Tạo và quản lý kết nối với database bằng HikariCP Connection Pool */
public class JDBCUtil {

    private static final Logger logger = LoggerFactory.getLogger(JDBCUtil.class);
    private static final HikariDataSource dataSource;

    static {
        try (InputStream input = JDBCUtil.class.getClassLoader().getResourceAsStream("server.properties")) {
            if (input == null) {
                throw new IllegalStateException("Không tìm thấy file server.properties trong classpath");
            }
            Properties props = new Properties();
            props.load(input);

            // 1. Đọc và kiểm tra thông tin cấu hình cơ bản
            String host = props.getProperty("db.host");
            String database = props.getProperty("db.name");
            String user = props.getProperty("db.user");
            String pass = props.getProperty("db.pass", "");

            if (host == null || database == null || user == null) {
                throw new IllegalStateException("Thiếu thông tin cấu hình Database (host, name, user) trong file server.properties");
            }

            String port = props.getProperty("db.port", "3306").trim();
            String url = "jdbc:mysql://" + host.trim() + ":" + port + "/" + database.trim() +
                    "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

            // 2. Cấu hình HikariCP
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(url);
            config.setUsername(user.trim());
            config.setPassword(pass);
            config.setPoolName("MyAppPool"); // Đặt tên pool để dễ nhận ra trong log

            // --- Các cấu hình tối ưu hiệu năng ---
            try {
                config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.max-size", "10").trim()));
                config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.min-idle", "2").trim()));
                config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idle-timeout", "30000").trim()));
                config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.conn-timeout", "20000").trim()));

                // Quan trọng: maxLifetime phải nhỏ hơn wait_timeout của MySQL (thường 8 giờ).
                // Mặc định HikariCP là 30 phút (1_800_000 ms) — set tường minh để tránh
                // kết nối bị MySQL cắt âm thầm khi app chạy lâu.
                config.setMaxLifetime(Long.parseLong(props.getProperty("db.pool.max-lifetime", "1800000").trim()));

            } catch (NumberFormatException e) {
                logger.warn("Lỗi định dạng số trong file cấu hình pool, sử dụng thông số mặc định.", e);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setIdleTimeout(30000);
                config.setConnectionTimeout(20000);
                config.setMaxLifetime(1_800_000);
            }

            // Cấu hình tối ưu riêng cho MySQL
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            // 3. Khởi tạo DataSource
            dataSource = new HikariDataSource(config);

            Runtime.getRuntime().addShutdownHook(new Thread(JDBCUtil::shutdownPool));

        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng khi khởi tạo Connection Pool", e);
            throw new ExceptionInInitializerError(e);
        }
    }

    private JDBCUtil() {}

    /**
     * Lấy kết nối từ Pool.
     */
    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource chưa được khởi tạo thành công hoặc đã bị đóng.");
        }
        return dataSource.getConnection();
    }

    /**
     * Đóng kết nối (trả về Pool).
     * Khuyến khích dùng try-with-resources thay vì gọi hàm này thủ công.
     */
    public static void closeConnection(Connection c) {
        if (c != null) {
            try {
                if (!c.isClosed()) {
                    c.close();
                }
            } catch (SQLException e) {
                logger.error("Lỗi khi đóng kết nối", e);
            }
        }
    }

    /**
     * Giải phóng hoàn toàn Pool khi tắt ứng dụng.
     */
    public static void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP Connection Pool đã đóng an toàn."); // info, không phải lỗi
        }
    }
}