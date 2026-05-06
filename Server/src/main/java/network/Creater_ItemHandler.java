package network;

import service.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;

/**
 * Xử lý request CREATE_ITEM từ người bán.
 *
 * Payload mong đợi: một Item hoặc subclass của Item.
 */
public class Creater_ItemHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể lưu item mới thông qua service layer.
     */
    public Creater_ItemHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload là Item object; out còn mở.
     * Postcondition: Gửi CREATE_ITEM_RESULT với true nếu lưu item và auction thành công.
     * Method không trả về giá trị.
     * NOTE: Payload không phải Item sẽ bị từ chối và trả về false.
     */
    public void handle(Object payload, ObjectOutputStream out) {

        // Kiểm tra và ép kiểu trực tiếp từ payload
        if (payload instanceof Item) {
            Item item = (Item) payload; // Ép kiểu về lớp cha
            // Thực hiện lưu trữ thông qua service
            boolean isSuccess = userService.creater_item(item);
            sendResponse(out, "CREATE_ITEM_RESULT", isSuccess);

        } else {
            System.err.println("Dữ liệu gửi lên không phải là Item!");
            sendResponse(out, "CREATE_ITEM_RESULT", false);
        }
    }
}
