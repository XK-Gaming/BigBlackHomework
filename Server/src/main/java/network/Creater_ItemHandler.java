package network;

import service.UserService;
import model.Items.Item;
import model.exception.PersistenceException;

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
            Item item = (Item) payload;
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
