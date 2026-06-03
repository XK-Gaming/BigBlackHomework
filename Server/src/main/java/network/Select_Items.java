package network;

import model.User.UserRole;
import service.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;
import java.util.ArrayList;

// Request danh sách sản phẩm.
public class Select_Items extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public Select_Items(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request danh sách sản phẩm.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        ArrayList<Item> result;

        String roleStr = (payload != null) ? String.valueOf(payload).trim() : "";

        try {

            if (roleStr.isEmpty()) {

                result = userService.select_items(null);
            } else {

                UserRole role = UserRole.valueOf(roleStr.toUpperCase());
                result = userService.select_items(role);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Server Lỗi] Không tìm thấy UserRole phù hợp với chuỗi: " + roleStr);
            result = new ArrayList<>();
        }

        sendResponse(out, Command.SELECT_ITEMS_RESULT, result);
    }
}
