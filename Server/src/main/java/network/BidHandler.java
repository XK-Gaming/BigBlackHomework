package network;

import model.auction.Auction;
import model.exception.AuctionException;
import service.UserService;

import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class BidHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;
        Map<String, Object> response = new HashMap<>();

        try {
            String itemId = String.valueOf(bidInfo.get("itemId"));
            String bidderId = String.valueOf(bidInfo.get("bidderId"));
            double amount = Double.parseDouble(String.valueOf(bidInfo.get("amount")));

            Map<String, Object> result = userService.processBid(itemId, bidderId, amount);

            if (result != null) {
                double newPrice = ((Number) result.get("newPrice")).doubleValue();

                response.put("success", true);
                response.put("message", "Đấu giá thành công");
                response.put("newPrice", newPrice);
                response.put("itemId", itemId);

                if (result.get("latestAuction") instanceof Auction latestAuction) {
                    Instant auctionEndTime = BidEventPublisher.extractAuctionEndTime(latestAuction);
                    if (auctionEndTime != null) {
                        response.put("auctionEndTime", auctionEndTime);
                    }
                }

                // Bid event: dùng helper chung để manual bid và AutoBid phát realtime giống nhau.
                BidEventPublisher.publishSuccessfulBid(itemId, bidderId, result);
            } else {
                response.put("success", false);
                response.put("message", "Đấu giá thất bại");
            }
        } catch (AuctionException e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại: " + e.getMessage());
        } catch (Exception e) {
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Đấu giá thất bại do lỗi hệ thống");
        }

        sendResponse(out, Command.BID_RESULT, response);
    }

}
