package dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Hợp đồng DAO dùng chung cho các class truy cập database trong module này.
 * NOTE: Interface đang trộn CRUD generic với overload riêng cho Auction/Item, vì vậy nhiều
 * implementation có method không liên quan và đang để stub.
 */
interface DaoInterface <T> {

    int Insert(T t) throws SQLException;

    int Update(Connection conn ,T t) throws SQLException;

    int Delete(T t);

    ArrayList<T> selectAll() throws SQLException;

    int Update(Connection con, model.Items.Item item) throws SQLException;

    ArrayList<T> moreSelectByCondition (String condition);
}