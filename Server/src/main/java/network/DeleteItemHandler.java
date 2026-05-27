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
            } else {
                // Thực hiện xóa trong cơ sở dữ liệu
                if (auctionItem != null) {
                    DAOAuction_Items.getInstance().Delete(selectedItem);
                }

                int rowsAffected = DAOItems.getInstance().Delete(selectedItem);

                if (rowsAffected > 0) {
                    // Cấu hình phản hồi thành công trả về riêng cho Seller
                    response.put("success", true);
                    response.put("message", "Xóa sản phẩm thành công!");
                    response.put("deletedItemId", itemId); // Gửi ID về lại cho Seller dọn giao diện riêng (nếu cần)
                    response.put("itemName", selectedItem.getName());

                    // ------------------------------------------------------------------
                    // LOGIC REALTIME: PHÁT TÍN HIỆU ĐẾN CÁC CLIENT KHÁC
                    // ------------------------------------------------------------------
                    // Tạo payload chứa thông tin xóa để gửi ra sảnh chính (Pagination công cộng)
                    Map<String, Object> broadcastData = new HashMap<>();
                    broadcastData.put("success", true);
                    broadcastData.put("deletedItemId", itemId);
                    broadcastData.put("itemName", selectedItem.getName());

                    System.out.println("[Server Realtime] Phát tín hiệu XÓA sản phẩm ra sảnh chính cho Item ID: " + itemId);

                    // Gửi cập nhật ra sảnh chính (roomID = null) với Command là ITEMS_UPDATE
                    // Khi nhận được gói tin này, ControllerBidder sẽ tự động kích hoạt hàm removeSingleItem(itemId)
                    AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, broadcastData);
                    // ------------------------------------------------------------------

                } else {
                    response.put("success", false);
                    response.put("message", "Không tìm thấy sản phẩm trong cơ sở dữ liệu để xóa.");
                }

                // Gửi kết quả cuối cùng về cho Seller vừa gửi yêu cầu
                sendResponse(out, Command.DELETE_ITEM_RESULT, response);
            }

        } catch (Exception e) {
            e.printStackTrace();
            fillErrorResponse(response, e);
            response.put("success", false);
            response.put("message", "Xóa thất bại do lỗi hệ thống.");

            // Đảm bảo luôn phản hồi về cho Seller khi có lỗi ngoại lệ xảy ra
            sendResponse(out, Command.DELETE_ITEM_RESULT, response);
        }
    }
}