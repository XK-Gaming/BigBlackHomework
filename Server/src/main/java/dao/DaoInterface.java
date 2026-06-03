package dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

interface DaoInterface <T> {

    int Insert(T t) throws SQLException;

    int Update(Connection conn ,T t) throws SQLException;

    int Delete(T t);

    ArrayList<T> selectAll() throws SQLException;

    int Update(Connection con, model.Items.Item item) throws SQLException;

    ArrayList<T> moreSelectByCondition (String condition);
}
