package network;

import Service_.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class Select_Items extends BaseHandler implements RequestHandler{
    private UserService userService;

    public Select_Items(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        ArrayList<Item> result = userService.select_items();
        sendResponse(out, "SELECT_ITEMS_RESULT", result);
    }
}

