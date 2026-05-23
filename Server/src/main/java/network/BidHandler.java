package network;

import model.Items.Item;
import model.auction.BidTransaction;
import service.UserService;
import model.auction.Auction;
import model.exception.AuctionException;

import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BidHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;

        Map<String, Object> response = new HashMap<>();

        try {
            String itemId = String.valueOf(bidInfo.get("itemId"));
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
                Instant auctionEndTime = latestAuction.getItem() != null
                        ? latestAuction.getItem().getAuctionEndTime()
                        : null;
                if (auctionEndTime != null) {
                    response.put("auctionEndTime", auctionEndTime);
                    bidUpdate.put("auctionEndTime", auctionEndTime);
                }
                String usernameOldBidder = null;
                try {
                usernameOldBidder = bidHistory.get(bidHistory.size() - 2).getBidder();}
                catch (Exception e){
                    usernameOldBidder = bidderId;
                }// Lấy username của người đặt giá trước đó
                Map<String, Object> notifPayload = new HashMap<>();
                notifPayload.put("item", item);
                notifPayload.put("auction", latestAuction);
                notifPayload.put("bidderId",bidderId);
                notifPayload.put("newPrice",newPrice);
                // Để cập nhật teeen toàn bộ các clients đang trong phiên đấu giá
                AuctionServer.broadcastToSpecificAuction(itemId, Command.BID_UPDATE, bidUpdate);
                AuctionServer.sendToSpecificUser(usernameOldBidder, Command.NOTIFICATION, notifPayload);
                AuctionServer.sendToSpecificUser(item.getSellerId(), Command.NOTIFICATION, notifPayload);
            } else {
                System.err.println("[Server Lỗi] Không thể phát tín hiệu ITEMS_UPDATE vì không tìm thấy dữ liệu đấu giá hiện tại!");
            }
        }else {
                response.put("success", false);
                response.put("message", "Đấu giá thất bại");
            }
        } catch (AuctionException e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại");
        } catch (Exception e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại");
        }

        sendResponse(out, Command.BID_RESULT, response);
    }
}