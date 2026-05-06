package network;

import service.UserService;
import model.auction.Auction;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý request SET_AUCTION, dùng để đánh dấu item client đang xem.
 *
 * Payload mong đợi: Map chứa itemId và userId.
 */
public class SetAuctionHandler extends BaseHandler implements RequestHandler {
    private UserService userService;
    private ClientHandler clientHandler;

    /**
     * Precondition: userService và clientHandler đã được khởi tạo cho kết nối hiện tại.
     * Postcondition: Handler có thể cập nhật item đang xem của ClientHandler này.
     */
    public SetAuctionHandler(UserService userService, ClientHandler clientHandler) {
        this.userService = userService;
        this.clientHandler = clientHandler;
    }

    @Override
    /**
     * Precondition: payload là Map chứa itemId và userId; out còn mở.
     * Postcondition: Gán ClientHandler.viewingItemId và gửi SET_AUCTION_RESULT với dữ liệu
     * auction hoặc message lỗi.
     * Method không trả về giá trị.
     * NOTE: itemId rỗng hoặc chuỗi "null" sẽ trả success=false mà không query auction.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> Lst = (Map) payload;
        String itemId_Str = String.valueOf(Lst.get("itemId"));
        String userId = (String) Lst.get("userId");

        // ✅ FIX: Validate itemId không rỗng
        if (itemId_Str == null || itemId_Str.isEmpty() || "null".equals(itemId_Str)) {
            System.err.println("❌ SetAuction: itemId rỗng hoặc null");
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "itemId rỗng");
            sendResponse(out, "SET_AUCTION_RESULT", errorResp);
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
                response.put("status", auction.getStatus());
                System.out.println("✅ SetAuction: Fetch auction thành công cho item " + itemId_Str);
            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy auction cho item này");
                System.err.println("❌ SetAuction: Không tìm được auction cho item " + itemId_Str);
            }

            sendResponse(out, "SET_AUCTION_RESULT", response);
        } catch (Exception e) {
            System.err.println("❌ Lỗi SetAuctionHandler: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "Lỗi: " + e.getMessage());
            sendResponse(out, "SET_AUCTION_RESULT", errorResp);
        }
    }
}
