package dao;

import java.util.ArrayList;

import model.Items.Item;
import model.User.Bidder;
import model.auction.Auction;
import model.auction.AuctionManager;
import model.exception.AuctionNotFoundException;
import service.AppServices;

public class DAOAution_Items {
    public static DAOAution_Items getInstance() {
        return new DAOAution_Items();
    }

    public int Insert(Auction auction, Item item1) {
        return auction == null || item1 == null ? 0 : 1;
    }

    public int Insert(Item item1) {
        return item1 == null ? 0 : 1;
    }

    public int Update(Auction auction, Item item1, Bidder leading) {
        return auction == null || item1 == null || leading == null ? 0 : 1;
    }

    public int Delete(Auction auction) {
        return 0;
    }

    public ArrayList<Auction> selectAll() {
        return new ArrayList<>(AuctionManager.getInstance().getAuctions());
    }

    public Auction selectByUsername(Auction auction) {
        return auction;
    }

    public Auction selectByItemId(Item item) {
        if (item == null) {
            return null;
        }
        try {
            return AppServices.auctionService().findAuctionByItemId(item.getId());
        } catch (AuctionNotFoundException exception) {
            return null;
        }
    }
}
