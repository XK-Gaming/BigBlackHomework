package network;

import service.UserService;
import model.Items.Item;
import model.exception.PersistenceException;

import java.io.ObjectOutputStream;

public class EditItemHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public EditItemHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        // Kiểm tra và ép kiểu trực tiếp từ payload (Item đã được sửa từ Client gửi lên)
        if (payload instanceof Item item) {
            try {
                // Gọi dịch vụ để cập nhật item vào Database (Server xử lý DB hoàn toàn)
                userService.updateItem(item);

                // Trả kết quả THÀNH CÔNG về cho chính Client vừa gửi yêu cầu sửa
                sendResponse(out, Command.EDIT_ITEM_RESULT, true);

                // Phát broadcast thông báo cho toàn bộ các Client khác biết danh sách sản phẩm vừa có thay đổi
                AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, item);

            } catch (PersistenceException e) {
                System.err.println("[EditItemHandler] " + e.getMessage());
                // Trả kết quả THẤT BẠI về cho Client nếu dính lỗi database (Ví dụ: dữ liệu lỗi hoặc phiên đã kết thúc)
                sendResponse(out, Command.EDIT_ITEM_RESULT, false);
            }
        } else {
            // Trả kết quả THẤT BẠI nếu dữ liệu gửi lên không phải là Object Item
            sendResponse(out, Command.EDIT_ITEM_RESULT, false);
        }
    }
}