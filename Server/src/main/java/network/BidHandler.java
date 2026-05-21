package network;

import model.Items.Item;
import model.auction.BidTransaction;
import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BidHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;

        Map<String, Object> response = new HashMap<>();

        try {
            Object rawItemId = bidInfo.get("itemId");
            if (rawItemId == null) {
                response.put("success", false);
                response.put("message", "itemId không được để trống");
                sendResponse(out, Command.BID_RESULT, response);
                return;
            }
            String itemId = String.valueOf(rawItemId);
            String bidderId = String.valueOf(bidInfo.get("bidderId"));
            double amount = Double.parseDouble(String.valueOf(bidInfo.get("amount")));
            Map<String,Object> result = userService.processBid(itemId, bidderId, amount);
            if (result != null){
                ArrayList<BidTransaction> bidHistory = (ArrayList<BidTransaction>) result.get("bidHistory");
                Auction latestAuction = (Auction) result.get("latestAuction");
                Item item = (Item) result.get("item");
                double newPrice = (double) result.get("newPrice");
                response.put("success", true);
                response.put("message", "Đấu giá thành công");
                response.put("newPrice", newPrice);
                response.put("itemId", itemId);

                // Broadcast cho toàn bộ client đang cùng xem item này.
                Map<String, Object> bidUpdate = new HashMap<>();
                bidUpdate.put("success", true);
                bidUpdate.put("itemId", itemId);
                bidUpdate.put("bidderId", bidderId);
                bidUpdate.put("newPrice", newPrice);

                if (latestAuction != null) {
                    bidUpdate.put("auction", latestAuction);
                }
                String usernameOldBidder = bidHistory.get(bidHistory.size() - 2).getBidder(); // Lấy username của người đặt giá trước đó
                Map<String, Object> notifPayload = new HashMap<>();
                notifPayload.put("item", item);
                notifPayload.put("auction", latestAuction);
                notifPayload.put("bidderId",bidderId);
                notifPayload.put("newPrice",newPrice);
                // Để cập nhật teeen toàn bộ các clients đang trong phiên đấu giá
                AuctionServer.broadcastToSpecificAuction(itemId, Command.BID_UPDATE, bidUpdate);
                AuctionServer.sendToSpecificUser(usernameOldBidder, Command.NOTIFICATION, notifPayload);
            } else {
                response.put("success", false);
                response.put("message", "Giá đặt phải cao hơn giá hiện tại");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
        }

        sendResponse(out, Command.BID_RESULT, response);
    }
}