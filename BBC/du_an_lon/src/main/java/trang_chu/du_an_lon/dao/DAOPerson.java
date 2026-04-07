package trang_chu.du_an_lon.dao;

import trang_chu.du_an_lon.database.JDBCUtil;
import trang_chu.du_an_lon.model.Person;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class DAOPerson implements DaoInterface<Person> {

    public static DAOPerson getInstance() {
        return new DAOPerson();
    }

    @Override
    public int Insert(Person person) {
        Connection con = JDBCUtil.getConnection();

        Statement st = null;
        try {
            st = con.createStatement();

            String sql = "INSERT INTO khach (username, password, name, tel) " +
                    " VALUES('" + person.getUsername() + "' , '" + person.getPassword() + "' , '" + person.getName() + "' , " + person.getAddress() + ")";
            int ketQua = st.executeUpdate(sql);
            JDBCUtil.closeConnection(con);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public int Update(Person person) {
        return 0;
    }

    @Override
    public int Delete(Person person) {
        return 0;
    }

    @Override
    public ArrayList selectAll() {
        return null;
    }

    @Override
    public Person selectByUsername(Person person) {
        return null;
    }

    public Person selectByUsername(String username, String password) {
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
                    return new Person(username, password, rs.getString("name"), rs.getString("tel"));
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
