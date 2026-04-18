package dao;

import model.Items.Item;

import java.sql.SQLException;
import java.util.ArrayList;

interface DaoInterface <T> {

    int Insert(T t);

    int Insert(Item item);

    public int Update(T t);

    public int Delete(T t);

    public ArrayList<T> selectAll() throws SQLException;

    public  T selectByUsername(T t);
    public ArrayList<T> selectByCondition (String condition);
}
