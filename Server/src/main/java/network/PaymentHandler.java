package network;

import model.Items.Item;
import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class PaymentHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public PaymentHandler(UserService userService) {
        this.userService = userService;
    }

    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> bidInfo = (Map<String, Object>) payload;
        Map<String, Object> response = new HashMap<>();
        try {
            Item item = (Item) bidInfo.get("itemId");
            boolean result = userService.PayHandler(item);
            if(result){
                response.put("newPayment", true);
                response.put("success", true);
                response.put("item", item);
                AuctionServer.broadcastToSpecificAuction(item.getSellerId(), Command.NOTIFICATION_NEW_PAY, response);
            }
            else{
                response.put("success", false);
            }
            sendResponse(out,Command.BIDDER_PAY_RESULT, response);


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
