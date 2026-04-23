package dao;

import model.Items.Item;
import model.User.Bidder;
import model.auction.Auction;

import java.sql.SQLException;
import java.util.ArrayList;

interface DaoInterface <T> {

    int Insert(T t);

    //Logic thêm sản phẩm__ dùng PrepareStatement
    int Insert(Auction auction, Item item1);

    int Insert(Item item);

    public int Update(T t);

    public int Delete(T t);

    public ArrayList<T> selectAll() throws SQLException;

    public  T selectByUsername(T t);
    public ArrayList<T> selectByCondition (String condition);
}
