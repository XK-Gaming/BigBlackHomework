package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý request BID.
 *
 * Payload mong đợi: Map chứa itemId, bidderId và amount.
 */
public class BidHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể validate và lưu bid qua service layer.
     */
    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload chứa itemId, bidderId và amount dạng số; out còn mở.
     * Postcondition: Gửi BID_RESULT cho client đặt giá. Nếu thành công, broadcast BID_UPDATE
     * tới mọi client online đang xem cùng item.
     * Method không trả về giá trị.
     * NOTE: Payload sai hoặc service lỗi sẽ trả success=false kèm message lỗi.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;

        Map<String, Object> response = new HashMap<>();

        try {
            String itemId = String.valueOf(bidInfo.get("itemId"));
            String bidderId = String.valueOf(bidInfo.get("bidderId"));
            double amount = Double.parseDouble(String.valueOf(bidInfo.get("amount")));

            double newPrice = userService.processBid(itemId, bidderId, amount);

            if (newPrice > 0) {
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
                System.out.println(bidUpdate);
                AuctionServer.broadcastToSpecificAuction(itemId, "BID_UPDATE", bidUpdate);
            } else {
                response.put("success", false);
                response.put("message", "Giá đặt phải cao hơn giá hiện tại");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Lỗi: " + e.getMessage());
        }

        sendResponse(out, "BID_RESULT", response);
    }
}
