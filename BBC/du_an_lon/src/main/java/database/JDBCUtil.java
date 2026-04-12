package database;

import com.mysql.cj.jdbc.Driver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
                        String url = "jdbc:mysql://localhost:3307/quanlydaugia";
                        String username = "root";
                        String password = "";
                        c = DriverManager.getConnection(url, username, password);
                    }}}
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
