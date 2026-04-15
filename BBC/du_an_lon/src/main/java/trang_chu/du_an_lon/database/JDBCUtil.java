package trang_chu.du_an_lon.database;

import com.mysql.cj.jdbc.Driver;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class JDBCUtil {
    public static Connection getConnection() {
        Connection c = null;
        try{
            DriverManager.registerDriver(new Driver());
            // Các thông số
            String url = "jdbc:mySQL://localhost:3307/quanlydaugia";
        String username = "root";
        String password = "";
        // Tạo kết nối
            c = DriverManager.getConnection(url, username, password);
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
