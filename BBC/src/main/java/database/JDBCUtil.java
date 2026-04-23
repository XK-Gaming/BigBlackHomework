package database;

import com.mysql.cj.jdbc.Driver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
/* Tạo kết nối với databse bằng JDBC */
public class JDBCUtil {
    private static  Connection c = null; //Biến static lưu trưc kết nối duy nhất

    private JDBCUtil(){}; // Private constructor để ngăn chặn khởi tạo từ bên ngoài

    // Phương thức lấy kết nối ( Dùng Singleton)
    public static Connection getConnection() {
        try{
            // Nếu chưa có kết nối hoặc kết nối đã bị đóng thì tạo mới
            if(c == null || c.isClosed()){
                synchronized (JDBCUtil.class){ // Đa luồng - chỉ một luồng cầm khóa tương tác với databse
                    if (c == null || c.isClosed()) {
                        DriverManager.registerDriver(new com.mysql.cj.jdbc.Driver());
                        String host = "db-daugia-java.mysql.database.azure.com";
                        String database = "quan_ly_dau_gia";
                        String user = "linhadmin";
                        String pass = "Linh@611";

                        // 2. Chuỗi URL với cấu hình SSL bắt buộc của Azure
                        String url = "jdbc:mysql://" + host + ":3306/" + database +
                                "?useSSL=true&requireSSL=false&serverTimezone=UTC";

                        // 3. Tạo kết nối
                        c = DriverManager.getConnection(url, user, pass);
                    }
                }
            }
        }
        catch (SQLException e){
            e.printStackTrace();

        }
        return c;
    }
    public static void closeConnection (Connection c){
        try{
            if(c!= null){
                c.close();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
