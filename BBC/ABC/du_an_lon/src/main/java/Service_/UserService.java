package Service_;

import dao.DAOAution_Items;
import dao.DAOItems;
import dao.DAOUser;
import model.Items.Item;
import model.User.User;
import model.auction.Auction;
import model.auction.AuctionStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService {
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

            if (DAOUser.selectByUsername(user.getUsername())) {
                response.put("success", false);
                response.put("message", "Tài khoản đã tồn tại");
                return response;
            }

            DAOUser.getInstance().Insert(user);
            response.put("success", true);
            response.put("message", "Đăng ký tài khoản thành công");
            return response;
        }


    public boolean creater_item(Item item) {
        itemDAO.Insert(item);
        Auction auction = new Auction("1", item, item.getSellerId(), item.getAuctionStartTime());
        auctionDAO.Insert(auction, item);
        return true;
    }

    public ArrayList<Item> select_items() {
        return DAOItems.selectedAll();
    }

    public Auction getAuctionByItemId(String itemId) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) return null;
        return auctionDAO.selectByItemId(item);
    }
    public void SetAuctionByItemId(String itemId, String userid) {
        // Không cần cập nhật user status nữa
        // Hàm này chỉ là để tracking/logging lần view của user
        // Dữ liệu auction sẽ được fetch lên khi client request GET_AUCTION
    }

    public double processBid(String itemId, String bidderId, double amount) {
        Item item = itemDAO.selectById(itemId);
        if (item == null) {
            System.err.println("❌ processBid: Item không tồn tại ID=" + itemId);
            return -1;
        }

        if (amount <= item.getCurrentHighestPrice()) {
            System.err.println("❌ processBid: Giá " + amount + " không cao hơn giá hiện tại " + item.getCurrentHighestPrice());
            return -1;
        }
        
        // ✅ Cập nhật giá trên object
        item.setCurrentHighestPrice(amount);
        
        // ✅ Cập nhật giá trong bảng items
        int updatedRows = itemDAO.Update(item, amount);
        if (updatedRows <= 0) {
            System.err.println("❌ processBid: Lỗi cập nhật giá item trong DB");
            return -1;
        }
        
        System.out.println("✅ processBid: Cập nhật giá item " + itemId + " thành " + amount);

        // ✅ Cập nhật leading bidder trong bảng auction_items
        Auction auction = auctionDAO.selectByItemId(item);

        if (auction == null) {
            System.err.println("❌ processBid: Không tìm thấy auction cho item ID=" + itemId);
            return -1;
        }

        try {
            // ✅ FIX: Copy bidHistory vào ArrayList mới (vì getBidHistory() trả về unmodifiable list)
            List<model.auction.BidTransaction> newHistory = new ArrayList<>(auction.getBidHistory());

            // Tạo transaction ID mới
            String transactionId = "BID-" + System.currentTimeMillis() + "-" + bidderId;
            model.auction.BidTransaction newBid = new model.auction.BidTransaction(
                transactionId,
                bidderId,
                amount,
                java.time.Instant.now()
            );
            newHistory.add(newBid);
            auction.setbidHistory(newHistory);

            // ✅ Cập nhật vào DB
            System.out.println("DEBUG processBid: Updating with leadingBidder=" + bidderId + ", historySize=" + newHistory.size() + ", amount=" + amount);
            int updateResult = auctionDAO.Update(auction, item.getDatabaseId(), bidderId, amount);
            if (updateResult > 0) {
                System.out.println("✅ processBid: Thêm BidTransaction: " + bidderId + " -> " + amount + " VNĐ");
            } else {
                System.err.println("❌ processBid: Lỗi cập nhật auction_items trong DB (rows affected: " + updateResult + ")");
                return -1;
            }
        } catch (Exception e) {
            System.err.println("❌ processBid: Lỗi khi addding BidTransaction: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
        return amount;
    }

    public void updateAuctionStatus(String auctionId, String itemId, String status) {
        Item item = itemDAO.selectById(itemId);
        if (item != null) {
            AuctionStatus auctionStatus = AuctionStatus.valueOf(status);
            Auction auction = auctionDAO.selectByItemId(item);
            if (auction != null) {
                auction.setStatus(auctionStatus);
                auctionDAO.Update_Status(auction, item, auctionStatus);
            }
        }
    }

    public List<Auction> getAllAuctions() {
        return auctionDAO.selectAll();
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
}