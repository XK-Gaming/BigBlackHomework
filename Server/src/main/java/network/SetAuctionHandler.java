package network;

import service.AuctionService;import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

// Request vào phòng đấu giá.
public class SetAuctionHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;
    private final ClientHandler clientHandler;

    public SetAuctionHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    // Xử lý request vào phòng đấu giá.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {

        @SuppressWarnings("unchecked")
        Map<String, Object> payloadMap = (Map<String,Object>) payload;
        String itemId_Str = String.valueOf(payloadMap.get("itemId"));
        String userId = (String) payloadMap.get("userId");

        if (itemId_Str == null || itemId_Str.isEmpty() || "null".equals(itemId_Str)) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "itemId rỗng");
            sendResponse(out, Command.SET_AUCTION_RESULT, errorResp);
            return;
        }

        clientHandler.setViewingItemId(itemId_Str);

        try {
            Auction auction = userService.getAuctionByItemId(itemId_Str);

            Map<String, Object> response = new HashMap<>();
            if (auction != null && auction.getStatus() != null) {
                response.put("success", true);
                response.put("auction", auction);
                response.put("itemId", itemId_Str);
                response.put("status", AuctionService.syncAuctionStatus(auction));
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
