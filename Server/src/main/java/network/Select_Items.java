package network;

import service.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;
import java.util.ArrayList;

/**
 * Xử lý request SELECT_ITEMS.
 *
 * Payload mong đợi: không sử dụng.
 */
public class Select_Items extends BaseHandler implements RequestHandler{
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể truy vấn danh sách item.
     */
    public Select_Items(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: out còn mở; payload bị bỏ qua.
     * Postcondition: Gửi SELECT_ITEMS_RESULT với ArrayList<Item> lấy từ database.
     * Method không trả về giá trị.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        ArrayList<Item> result = userService.select_items();
        sendResponse(out, "SELECT_ITEMS_RESULT", result);
    }
}

