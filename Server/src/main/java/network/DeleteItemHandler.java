package network;

import dao.DAOAuction_Items;
import dao.DAOItems;
import model.Items.Item;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class DeleteItemHandler extends BaseHandler implements RequestHandler {

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Kiểm tra và ép kiểu đúng về đối tượng Item
            if (!(payload instanceof Item)) {
                throw new IllegalArgumentException("Dữ liệu yêu cầu xóa không hợp lệ! Mong đợi đối tượng Item.");
            }

            // Ép kiểu về Item trước
            Item selectedItem = (Item) payload;

            // Rút ID số nguyên ra để dùng
            int itemId = selectedItem.getDatabaseId();

            // 2. Kiểm tra xem sản phẩm đã có ai đặt giá chưa
            var auctionItem = DAOAuction_Items.getInstance().selectByItemId(selectedItem);
            boolean hasBids = (auctionItem != null
                    && auctionItem.getBidHistory() != null
                    && !auctionItem.getBidHistory().isEmpty());

            if (hasBids) {
                response.put("success", false);
                response.put("message", "Không thể xóa! Sản phẩm này đã có người tham gia đặt giá.");

                // Gửi phản hồi thất bại về riêng cho Seller
                sendResponse(out, Command.DELETE_ITEM_RESULT, response);
                return; // Ngắt luồng luôn cho gọn sạch
            }

            if (auctionItem != null) {
                DAOAuction_Items.getInstance().Delete(selectedItem);
            }

            int rowsAffected = DAOItems.getInstance().Delete(selectedItem);

            if (rowsAffected > 0) {
                // 1. Chuẩn bị phản hồi thành công trả về riêng cho Seller
                response.put("success", true);
                response.put("message", "Xóa sản phẩm thành công!");
                response.put("deletedItemId", itemId);
                response.put("itemName", selectedItem.getName());

                // 2. REALTIME SẢNH CHÍNH: Xóa item khỏi danh sách hiển thị công cộng
                Map<String, Object> broadcastData = new HashMap<>();
                broadcastData.put("success", true);
                broadcastData.put("deletedItemId", itemId);
                broadcastData.put("itemName", selectedItem.getName());

                System.out.println("[Server Realtime] Phát tín hiệu XÓA sản phẩm ra sảnh chính cho Item ID: " + itemId);
                AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, broadcastData);

                // 3. REALTIME PHÒNG ĐẤU GIÁ: Đuổi những người KHÁC đang xem ra ngoài
                // Để an toàn, payload gửi đi nên thống nhất cấu trúc Map giống như phản hồi gốc,
                // giúp Client dễ parse data trong hàm onServerResponse.
                Map<String, Object> roomData = new HashMap<>();
                roomData.put("success", false); // false vì đối với người xem thì đây là một sự cố/phiên bị hủy
                roomData.put("message", "Sản phẩm (ID: " + itemId + ") đã bị gỡ bỏ bởi ban quản trị hoặc người bán.");
                roomData.put("isForceClose", true); // Flag để Client biết mà văng ra màn hình chính

                System.out.println("[Server] Đã gửi thông báo đóng phòng đấu giá " + itemId + " tới các client đang xem.");
                AuctionServer.broadcastToSpecificAuction(String.valueOf(itemId), Command.DELETE_ITEM_RESULT, roomData);

            } else {
                response.put("success", false);
                response.put("message", "Không tìm thấy sản phẩm trong cơ sở dữ liệu để xóa.");
            }

            // 4. Gửi phản hồi trực tiếp cho Seller (Nếu Seller đang xem phòng này, gói tin roomData ở trên sẽ được ghi đè/bổ sung bằng gói này, đảm bảo hiển thị đúng logic "Xóa thành công")
            sendResponse(out, Command.DELETE_ITEM_RESULT, response);

        } catch (Exception e) {
            e.printStackTrace();
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Xóa thất bại do lỗi hệ thống.");
            sendResponse(out, Command.DELETE_ITEM_RESULT, response);
        }
    }
}