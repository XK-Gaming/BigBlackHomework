package dao;

import database.JDBCUtil;
import model.User.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class DAOUser implements DaoInterface<User> {

    public static DAOUser getInstance() {
        return new DAOUser();
    }

    @Override
    public int Insert(User user) {
        Connection con = JDBCUtil.getConnection();

        Statement st = null;
        try {
            st = con.createStatement();

            String sql = "INSERT INTO khach (username, password, name, email, role) " +
                    " VALUES('" + user.getUsername() + "', '" + user.getPassword() + "', '" +
                    user.getName() + "', '" + user.getAddress() + "', '" + user.getRole() + "')";
            int ketQua = st.executeUpdate(sql);
            JDBCUtil.closeConnection(con);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public int Update(User user) {
        return 0;
    }

    @Override
    public int Delete(User user) {
        return 0;
    }

    @Override
    public ArrayList selectAll() {
        return null;
    }

    @Override
    public User selectByUsername(User user) {
        return null;
    }

    public User selectByUsername(String username, String password) {
        Connection con = JDBCUtil.getConnection();

        Statement st = null;
        try {
            st = con.createStatement();

            String sql = "SELECT * FROM khach where username = '" + username + "'";
            ResultSet rs = st.executeQuery(sql);
            if (!rs.next()) {
                return null;
            } else {
                String check_username = rs.getString("username");
                String check_password = rs.getString("password");
                if (check_password.equals(password) && check_username.equals(username)) {
                    if(rs.getString("role").equals("Người bán")){
                        return new Seller(check_username,check_password,rs.getString("name"),rs.getString("email"));
                    } else if (rs.getString("role").equals("Người đấu giá")) {
                        return new Bidder(check_username,check_password,rs.getString("name"),rs.getString("email"));
                    } else if (rs.getString("role").equals("Admin")) {
                        return new Admin(check_username,check_password,rs.getString("name"),rs.getString("email"));
                    }

                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        finally {
            // Đừng quên đóng connection để tránh tràn bộ nhớ
            JDBCUtil.closeConnection(con);
        }
        return null;
    }

    @Override
    public ArrayList selectByCondition(String condition) {
        return null;
    }
}
