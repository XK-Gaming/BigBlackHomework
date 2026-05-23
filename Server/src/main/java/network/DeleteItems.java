package network;

import service.UserService;

import java.io.ObjectOutputStream;

public class DeleteItems extends BaseHandler implements RequestHandler {
    private UserService userService;
    public DeleteItems(UserService userService) { this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
    int item_id = (Integer) payload;
    int result = userService.DeleteItem(item_id);
    AuctionServer.broadcastToSpecificAuction(String.valueOf(item_id), Command.DELETE_ITEM_RESULT, result);

    sendResponse(out, Command.DELETE_ITEM_RESULT, result);}

}
