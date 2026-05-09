package network;

import service.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;

public class Creater_ItemHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public Creater_ItemHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {

        // Kiểm tra và ép kiểu trực tiếp từ payload
        if (payload instanceof Item) {
            Item item = (Item) payload; // Ép kiểu về lớp cha
            // Thực hiện lưu trữ thông qua service
            boolean isSuccess = userService.creater_item(item);
            sendResponse(out, Command.CREATE_ITEM_RESULT, isSuccess);
            AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE , item);
            // Broadcast cập nhật danh sách item mới cho tất cả client

        } else {
            sendResponse(out, Command.CREATE_ITEM_RESULT, false);
        }
    }
}
