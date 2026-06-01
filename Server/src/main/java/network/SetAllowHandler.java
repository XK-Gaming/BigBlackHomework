package network;

import model.auction.Auction;
import service.UserService;
import network.Command;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class SetAllowHandler extends BaseHandler implements RequestHandler{
    private UserService userService;

    public SetAllowHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> Allow = (Map<String, Object>) payload;
        String allow = String.valueOf(Allow.get("allow"));
        System.out.println(allow);
        String itemId = String.valueOf(Allow.get("itemId"));

        Map<String, Object> response = new HashMap<>();
        if (itemId == null || itemId.isBlank() || "null".equalsIgnoreCase(itemId)) {
            response.put("success", false);
            response.put("message", "itemId rong");
            sendResponse(out, Command.SET_ALLOW_RESULT, response);
            return;
        }

        Auction auction = userService.setAllow(itemId, allow);
        response.put("success", auction != null);
        response.put("itemId", itemId);
        response.put("auction", auction);
        System.out.println(auction.getStatus());

        sendResponse(out, Command.SET_ALLOW_RESULT, response);
        AuctionServer.broadcastToSpecificAuction(itemId, Command.SET_ALLOW_RESULT, response);
    }
}
