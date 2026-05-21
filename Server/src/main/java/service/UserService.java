package service;

import dao.DAOAution_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.User.User;
import model.User.UserRole;
import model.auction.Auction;
import model.auction.AuctionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService {
    // Tạo một biến static ConcurrentHashMap trong toàn bộ Server...
    // Dù có nhiều ClientHandler chạy nhiều đối tượng UserService
    // thì vẫn đều chạy một hashmap chung
    private static final Map<String, Object> itemLocks = new java.util.concurrent.ConcurrentHashMap<>();
    private DAOUser userDAO = DAOUser.getInstance();
    private DAOItems itemDAO = DAOItems.getInstance();
    private DAOAution_Items auctionDAO = DAOAution_Items.getInstance();


    public User loginAndGetUser(String username, String password) {
        User user = userDAO.selectByUsername(username, password);
        if (user != null && user.getPassword().equals(password)) {
            return user;
        }
        return null;
    }
        public Map<String, Object> register(User user) {
            Map<String, Object> response = new HashMap<>();
            // Check Exception: Mật khẩu, tên đăng nhập không hợp lệ -- FALSE
            if (DAOUser.selectByUsername(user.getUsername())) {
                response.put("success", "EXSITED");
                return response;
            }

            DAOUser.getInstance().Insert(user);
            response.put("success", "TRUE");
            return response;
        }


    public boolean creater_item(Item item) {
        itemDAO.Insert(item);
        Auction auction = new Auction("1", item, item.getSellerId(), item.getAuctionStartTime());
        auctionDAO.Insert(auction, item);
        return true;
    }

    public ArrayList<Item> select_items(UserRole role) {
        ArrayList<Item> list = itemDAO.selectAll();
        if(role == UserRole.ADMIN){
            return list;
        }
        list.removeIf(item -> item.getAuctionStatus() == null);
        return list;
    }

    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        return auctionDAO.selectByItemId(item);
    }


    public Map<String, Object> processBid(String itemId, String bidderId, double amount) {
        Object lock = itemLocks.computeIfAbsent(itemId, k -> new Object());
        // Là khóa the id của item để đảm bảo chỉ một thread được
        // phép xử lý bid cho item đó tại một thời điểm.
        // Cơ chế computeIfAbsent đảm bảo rằng nếu đã có khóa cho itemId
        // thì sẽ trả về khóa đó, nếu chưa có thì sẽ tạo mới và trả về.
        synchronized (lock) {
        Item item = itemDAO.selectById(itemId);
        User user = userDAO.selectByUsernameOnly(bidderId);
        if (item == null) {return null;}
        if (amount <= item.getCurrentHighestPrice()) {return null;}
        double oldHighestPrice = item.getCurrentHighestPrice();

        if(amount > user.getBalance()) {return null;}
        else {userDAO.UpdateBalance(bidderId, user.getBalance() - amount);}
        item.setCurrentHighestPrice(amount);

        int updatedRows = itemDAO.Update(item);
        if (updatedRows <= 0) {return null;}

        // Cập nhật leading bidder trong bảng auction_items
        Auction auction = auctionDAO.selectByItemId(item);
        String OldBidder = auction.getLeadingBidder();
        User UserOldBidder = userDAO.selectByUsernameOnly(OldBidder);
        if (UserOldBidder != null) {
            userDAO.UpdateBalance(OldBidder, UserOldBidder.getBalance() + oldHighestPrice);
        }

        if (auction == null) {return null;}

        try {
            List<model.auction.BidTransaction> newHistory = new ArrayList<>(auction.getBidHistory());
            String transactionId = "BID-" + System.currentTimeMillis() + "-" + bidderId;
            model.auction.BidTransaction newBid = new model.auction.BidTransaction(
                transactionId,
                bidderId,
                amount,
                java.time.Instant.now()
            );
            newHistory.add(newBid);
            auction.setbidHistory(newHistory);

            int updateResult = auctionDAO.Update(auction, item.getDatabaseId(), bidderId, amount);
            if (updateResult > 0) {} else {return null;}
        } catch (Exception e) {
            System.err.println("❌ processBid: Lỗi khi addding BidTransaction: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
            Map<String, Object> finalResult = new HashMap<>();
            finalResult.put("item", item);
            finalResult.put("user", user);
            finalResult.put("latestAuction", auction); // Khớp với (Auction) result.get("latestAuction")
            finalResult.put("newPrice", amount);       // Khớp với (double) result.get("newPrice")
            finalResult.put("bidHistory", new ArrayList<>(auction.getBidHistory())); // Trả về dạng ArrayList chuẩn
            //
            //   Trả về cả item, user, latestAuction, newPrice, và bidHistory để client có thể cập nhật toàn bộ giao diện một cách chính xác.}
            return finalResult;
        }
    }

    public void updateAuctionStatus(String auctionId, String itemId, String status) {
        Item item = itemDAO.selectById(itemId);
        if (item != null) {
            AuctionStatus auctionStatus = AuctionStatus.valueOf(status);
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setStatus(auctionStatus);
                auctionDAO.Update_Status(item, auctionStatus);
            }
        }
    }

    public List<Auction> getAllAuctions() {
        List<Auction> list = auctionDAO.selectAll();
        list.removeIf(auction -> auction.getStatus() == null);
        return list;
    }

    public boolean updateUser(String username, String field, String value) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        switch (field) {
            case "name":
                user.setName(value);
                break;
            case "phone":
                break;
            case "address":
                user.setAddress(value);
                break;
            default:
                return false;
        }

        userDAO.Update(user);
        return true;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        if (!user.getPassword().equals(oldPassword)) {
            return false;
        }

        user.setPassword(newPassword);
        userDAO.Update(user);
        return true;
    }

    public void logout(String username) {
    }

    public Auction setAllow(String iditem, String choose) {
        Item item = itemDAO.selectById(iditem);
        if (item == null) {
            return null;
        }

        AuctionStatus nextStatus = Boolean.parseBoolean(choose) ? AuctionStatus.OPEN : null;
        auctionDAO.Update_Status(item, nextStatus);

        Auction auction = auctionDAO.selectByItemId(item);
        if (auction != null && auction.getItem() != null) {
            auction.getItem().setAuctionStatus(auction.getStatus());
        }
        return auction;
    }

    public int DeleteItem(int id_item) {
        Item item = itemDAO.selectById(String.valueOf(id_item));
        if (item != null) {
            return itemDAO.Delete(item);
        }
        return  0;
    }

    public boolean rechargeAmount(String username, double amount) {
        User user = userDAO.selectByUsernameOnly(username);
        if (user == null) return false;

        double newBalance = user.getBalance() + amount;
        userDAO.UpdateBalance(username, newBalance);
        return true;
    }

    public ArrayList getBidHistory(String itemId) {
        Auction auction = auctionDAO.selectByItemId(itemDAO.selectById(itemId));
        if (auction != null) {
            return new ArrayList<>(auction.getBidHistory());
    }
    return null;
    }
}
