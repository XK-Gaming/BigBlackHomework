package dao;

import java.util.ArrayList;

import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import service.AppServices;

public class DAOUser implements DaoInterface<User> {

    public static DAOUser getInstance() {
        return new DAOUser();
    }

    @Override
    public int Insert(User user) {
        AppServices.userRepository().save(user);
        return 1;
    }

    @Override
    public int Insert(Auction auction, Item item1) {
        return 0;
    }

    @Override
    public int Insert(Item item) {
        return 0;
    }

    @Override
    public int Update(User user) {
        if (user == null) {
            return 0;
        }
        AppServices.userRepository().save(user);
        return 1;
    }

    @Override
    public int Delete(User user) {
        return 0;
    }

    @Override
    public ArrayList<User> selectAll() {
        return new ArrayList<>(AppServices.userRepository().findAll());
    }

    @Override
    public User selectByUsername(User user) {
        if (user == null) {
            return null;
        }
        return AppServices.userRepository().findByUsername(user.getUsername()).orElse(null);
    }

    public static boolean selectByUsername(String username) {
        return AppServices.userRepository().existsByUsername(username);
    }

    public User selectByUsername(String username, String password) {
        User user = AppServices.userRepository().findByUsername(username).orElse(null);
        if (user == null) {
            return null;
        }
        return user.authenticate(password) ? user : null;
    }

    @Override
    public ArrayList<User> selectByCondition(String condition) {
        return new ArrayList<>(AppServices.userRepository().findAll());
    }
}
