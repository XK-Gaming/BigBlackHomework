package network;

import service.AuctionService;import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class SetAuctionHandler extends BaseHandler implements RequestHandler {
    private UserService userService;
    private ClientHandler clientHandler;

    public SetAuctionHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> Lst = (Map) payload;
        String itemId_Str = String.valueOf(Lst.get("itemId"));
        String userId = (String) Lst.get("userId");

        if (itemId_Str == null || itemId_Str.isEmpty() || "null".equals(itemId_Str)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "itemId rỗng");
            sendResponse(out, Command.SET_AUCTION_RESULT, errorResp);
            return;
        }

        // ✅ IMPROVED: Track user viewing item
        userService.SetAuctionByItemId(itemId_Str, userId);
        clientHandler.setViewingItemId(itemId_Str);

        // ✅ IMPROVED: Fetch auction data và response cho client
        try {
            Auction auction = userService.getAuctionByItemId(itemId_Str);

            Map<String, Object> response = new HashMap<>();
            if (auction != null) {
                response.put("success", true);
                response.put("auction", auction);
                response.put("itemId", itemId_Str);
                response.put("status", AuctionService.updateStatusByTime(auction));
            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy auction cho item này");
            }

            sendResponse(out, Command.SET_AUCTION_RESULT, response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "Lỗi: " + e.getMessage());
            sendResponse(out, Command.SET_AUCTION_RESULT, errorResp);
        }
    }
}
