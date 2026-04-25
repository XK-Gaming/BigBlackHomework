package dao;

import java.util.ArrayList;

import model.Items.Item;
import model.auction.Auction;
import service.AppServices;

public class DAOItems implements DaoInterface<Item> {
    public static DAOItems getInstance() {
        return new DAOItems();
    }

    @Override
    public int Insert(Item item) {
        return item == null ? 0 : 1;
    }

    @Override
    public int Update(Item item) {
        return item == null ? 0 : 1;
    }

    @Override
    public int Insert(Auction auction, Item item1) {
        return auction == null || item1 == null ? 0 : 1;
    }

    public int Update(Item item, Double currentPrice) {
        return item == null || currentPrice == null ? 0 : 1;
    }

    @Override
    public int Delete(Item item) {
        return 0;
    }

    @Override
    public ArrayList<Item> selectAll() {
        return selectedAll();
    }

    public static ArrayList<Item> selectedAll() {
        ArrayList<Item> items = new ArrayList<>();
        AppServices.catalogService().listCatalogItems().forEach(view -> items.add(view.item()));
        return items;
    }

    @Override
    public Item selectByUsername(Item item) {
        return item;
    }

    @Override
    public ArrayList<Item> selectByCondition(String condition) {
        return selectedAll();
    }
}
