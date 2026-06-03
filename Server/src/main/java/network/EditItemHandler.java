package network;

import service.UserService;
import model.Items.Item;
import model.exception.PersistenceException;

import java.io.ObjectOutputStream;

// Request sửa sản phẩm.
public class EditItemHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public EditItemHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request sửa sản phẩm.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {

        if (payload instanceof Item item) {
            try {

                userService.updateItem(item);

                sendResponse(out, Command.EDIT_ITEM_RESULT, true);

                Object allAuctionsLatest = userService.getAllAuctions();
                AuctionServer.broadcastToSpecificAuction(null, Command.ITEMS_UPDATE, item);

            } catch (PersistenceException e) {
                System.err.println("[EditItemHandler] " + e.getMessage());

                sendResponse(out, Command.EDIT_ITEM_RESULT, false);
            }
        } else {

            sendResponse(out, Command.EDIT_ITEM_RESULT, false);
        }
    }
}
