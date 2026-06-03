package network;

import model.Items.Item;
import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

// Request thanh toán.
public class PaymentHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public PaymentHandler(UserService userService) {
        this.userService = userService;
    }
    // Xử lý request thanh toán.
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;
        Map<String, Object> response = new HashMap<>();
        try {

            Item item = (Item) bidInfo.get("item");

            if (item == null) {
                System.err.println("[Server] Giao dịch thất bại: Đối tượng Item bị null trong gói tin BIDDER_PAY!");
                response.put("success", false);
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
                return;
            }

            if (item.getSellerId() == null || item.getSellerId().isBlank()) {
                response.put("success", false);
                response.put("message", "Seller id missing for item: " + item.getDatabaseId());
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
                return;
            }

            if (userService.getUserOnly(item.getSellerId()) == null) {
                response.put("success", false);
                response.put("message", "Seller not found: " + item.getSellerId());
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
                return;
            }

            boolean result = userService.PayHandler(item);
            if (result) {
                response.put("newPayment", true);
                response.put("success", true);
                response.put("item", item);
                AuctionServer.sendToSpecificUser(item.getSellerId(), Command.NOTIFICATION_BIDDER_PAY, response);
            } else {

                model.auction.Auction auction = userService.getAuctionByItemId(String.valueOf(item.getDatabaseId()));
                if (auction == null) {
                    response.put("message", "Auction not found for item: " + item.getDatabaseId());
                } else {
                    response.put("message", "Payment processing failed for item: " + item.getDatabaseId());
                }
                response.put("success", false);
            }
            sendResponse(out, Command.BIDDER_PAY_RESULT, response);

        } catch (Exception e) {
            System.err.println("[Server] Lỗi nghiêm trọng tại PaymentHandler: " + e.getMessage());
            e.printStackTrace();

            response.put("success", false);
            try {
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
