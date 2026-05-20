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
        // Khởi tạo map phản hồi kết quả về cho Client
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Ép kiểu payload nhận được từ Client (Client sẽ gửi đối tượng Item cần xóa lên)
            if (!(payload instanceof Item)) {
                throw new IllegalArgumentException("Dữ liệu sản phẩm yêu cầu xóa không hợp lệ!");
            }
            Item selectedItem = (Item) payload;

            // 2. Kiểm tra lịch sử đặt giá (Bid History) trước khi xóa để đảm bảo an toàn dữ liệu
            var auctionItem = DAOAuction_Items.getInstance().selectByItemId(selectedItem);
            boolean hasBids = (auctionItem != null
                    && auctionItem.getBidHistory() != null
                    && !auctionItem.getBidHistory().isEmpty());

            if (hasBids) {
                // Nếu đã có người đặt giá, từ chối xóa và gửi thông báo lỗi lý do về Client
                response.put("success", false);
                response.put("message", "Không thể xóa! Sản phẩm này đã có người tham gia đặt giá.");
            } else {
                // 3. Tiến hành xóa dữ liệu dưới CSDL nếu hợp lệ
                if (auctionItem != null) {
                    DAOAuction_Items.getInstance().Delete(selectedItem); // Xóa ở bảng cuộc đấu giá trước (khóa ngoại)
                }
                DAOItems.getInstance().Delete(selectedItem); // Xóa trực tiếp ở bảng sản phẩm

                // Phản hồi thành công về cho Client
                response.put("success", true);
                response.put("message", "Xóa sản phẩm thành công!");
                response.put("deletedItemId", selectedItem.getDatabaseId()); // Gửi kèm ID để Client dọn dẹp giao diện
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Hàm dùng chung từ BaseHandler để gán thông tin lỗi hệ thống
            fillErrorResponse(response, e);
        }

        // 4. Gửi gói tin kết quả về Client
        // Giả định bạn đã thêm DELETE_ITEM_RESULT vào Enum Command
        sendResponse(out, Command.DELETE_ITEM_RESULT, response);
    }
}