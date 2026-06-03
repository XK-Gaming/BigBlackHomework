package network;

import model.User.User;
import model.auction.Auction;
import model.exception.AuctionException;
import service.UserService;

import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

// Request đặt bid.
public class BidHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public BidHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request đặt bid.
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
                if (result.get("user") instanceof User updatedUser) {
                    response.put("user", updatedUser);
                    response.put("balance", updatedUser.getBalance());
                }

                if (result.get("latestAuction") instanceof Auction latestAuction) {
                    Instant auctionEndTime = BidEventPublisher.extractAuctionEndTime(latestAuction);
                    if (auctionEndTime != null) {
                        response.put("auctionEndTime", auctionEndTime);
                    }
                }

                BidEventPublisher.publishSuccessfulBid(itemId, bidderId, result);

                try {
                    if (result.get("bidHistory") != null) response.put("bidHistory", result.get("bidHistory"));
                    if (result.get("latestAuction") != null) response.put("auction", result.get("latestAuction"));
                    if (result.get("item") != null) response.put("item", result.get("item"));
                } catch (Exception ignored) {
                }

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

        System.out.println("[Server Debug] BID_HANDLER response: " + response);
        sendResponse(out, Command.BID_RESULT, response);
    }

}
