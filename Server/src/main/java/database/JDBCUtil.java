package database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

// Kết nối database.
public class JDBCUtil {

    private static final Logger logger = LoggerFactory.getLogger(JDBCUtil.class);
    private static final HikariDataSource dataSource;

    static {
        try (InputStream input = openConfigStream()) {
            if (input == null) {
                throw new IllegalStateException("Không tìm thấy file database.properties trong classpath");
            }
            Properties props = new Properties();
            props.load(input);

            String host = props.getProperty("db.host");
            String database = props.getProperty("db.name");
            String user = props.getProperty("db.user");
            String pass = props.getProperty("db.pass", "");

            if (host == null || database == null || user == null) {
                throw new IllegalStateException("Thiếu thông tin cấu hình Database (host, name, user) trong file server.properties");
            }

            String port = props.getProperty("db.port", "3306").trim();
            String url = "jdbc:mysql://" + host.trim() + ":" + port + "/" + database.trim() +
                    "?useSSL=true&requireSSL=true&verifyServerCertificate=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(url);
            config.setUsername(user.trim());
            config.setPassword(pass);
            config.setPoolName("MyAppPool");

            try {
                config.setMaximumPoolSize(Integer.parseInt(props.getProperty("db.pool.max-size", "40").trim()));
                config.setMinimumIdle(Integer.parseInt(props.getProperty("db.pool.min-idle", "2").trim()));
                config.setIdleTimeout(Long.parseLong(props.getProperty("db.pool.idle-timeout", "30000").trim()));
                config.setConnectionTimeout(Long.parseLong(props.getProperty("db.pool.conn-timeout", "20000").trim()));

                config.setMaxLifetime(Long.parseLong(props.getProperty("db.pool.max-lifetime", "1800000").trim()));

            } catch (NumberFormatException e) {
                logger.warn("Lỗi định dạng số trong file cấu hình pool, sử dụng thông số mặc định.", e);
                config.setMaximumPoolSize(10);
                config.setMinimumIdle(2);
                config.setIdleTimeout(30000);
                config.setConnectionTimeout(20000);
                config.setMaxLifetime(1_800_000);
            }

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            dataSource = new HikariDataSource(config);

            Runtime.getRuntime().addShutdownHook(new Thread(JDBCUtil::shutdownPool));

        } catch (Exception e) {
            logger.error("Lỗi nghiêm trọng khi khởi tạo Connection Pool", e);
            throw new ExceptionInInitializerError(e);
        }
    }
    // Mở tài nguyên.
    private static InputStream openConfigStream() {
        ClassLoader classLoader = JDBCUtil.class.getClassLoader();
        InputStream input = classLoader.getResourceAsStream("database.properties");
        if (input != null) {
            return input;
        }
        return classLoader.getResourceAsStream("server.properties");
    }

    private JDBCUtil() {}

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource chưa được khởi tạo thành công hoặc đã bị đóng.");
        }
        return dataSource.getConnection();
    }
    // Đóng socket.
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
    // Đóng tài nguyên.
    public static void shutdownPool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("HikariCP Connection Pool đã đóng an toàn.");
        }
    }
}
