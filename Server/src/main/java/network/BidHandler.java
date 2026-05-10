package network;

import service.UserService;
import model.auction.Auction;
import model.exception.AuctionException;

import java.io.ObjectOutputStream;
import java.util.HashMap;
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
            String itemId = String.valueOf(bidInfo.get("itemId"));
            String bidderId = String.valueOf(bidInfo.get("bidderId"));
            double amount = Double.parseDouble(String.valueOf(bidInfo.get("amount")));

            double newPrice = userService.processBid(itemId, bidderId, amount);

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

            Auction latestAuction = userService.getAuctionByItemId(itemId);
            if (latestAuction != null) {
                bidUpdate.put("auction", latestAuction);
            }
            AuctionServer.broadcastToSpecificAuction(itemId, Command.BID_UPDATE, bidUpdate);
        } catch (AuctionException e) {
            fillErrorResponse(response, e);
        } catch (Exception e) {
            fillErrorResponse(response, e);
        }

        sendResponse(out, Command.BID_RESULT, response);
    }
}