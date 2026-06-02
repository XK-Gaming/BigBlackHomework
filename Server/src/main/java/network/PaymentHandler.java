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
            // SỬA TẠI ĐÂY: Thay "itemId" bằng "item" để khớp với Client gửi lên
            Item item = (Item) bidInfo.get("item");

            // THÊM ĐOẠN KIỂM TRA NÀY: Phòng trường hợp lỗi truyền nhận dữ liệu qua mạng
            if (item == null) {
                System.err.println("[Server] Giao dịch thất bại: Đối tượng Item bị null trong gói tin BIDDER_PAY!");
                response.put("success", false);
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
                return; // Dừng xử lý luôn, không để chạy xuống dưới gây sập app
            }

            boolean result = userService.PayHandler(item);
            if(result){
                response.put("newPayment", true);
                response.put("success", true);
                response.put("item", item);
                AuctionServer.broadcastToSpecificAuction(item.getSellerId(), Command.NOTIFICATION_BIDDER_PAY, response);
            }
            else{
                response.put("success", false);
            }
            sendResponse(out, Command.BIDDER_PAY_RESULT, response);

        } catch (Exception e) {
            System.err.println("[Server] Lỗi nghiêm trọng tại PaymentHandler: " + e.getMessage());
            e.printStackTrace();
            // Trả về lỗi cho client thay vì quăng RuntimeException làm sập luồng của hệ thống
            response.put("success", false);
            try {
                sendResponse(out, Command.BIDDER_PAY_RESULT, response);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}
