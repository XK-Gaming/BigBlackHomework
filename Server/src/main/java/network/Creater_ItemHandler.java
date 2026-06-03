package network;

import service.UserService;
import model.Items.Item;
import model.exception.PersistenceException;

import java.io.ObjectOutputStream;

// Request tạo sản phẩm.
public class Creater_ItemHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public Creater_ItemHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request tạo sản phẩm.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {

        if (payload instanceof Item item) {
            try {
                userService.creater_item(item);
                sendResponse(out, Command.CREATE_ITEM_RESULT, true);
                AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, item);
            } catch (PersistenceException e) {
                System.err.println("[Creater_ItemHandler] " + e.getMessage());
                sendResponse(out, Command.CREATE_ITEM_RESULT, false);
            }

        } else {
            sendResponse(out, Command.CREATE_ITEM_RESULT, false);
        }
    }
}
