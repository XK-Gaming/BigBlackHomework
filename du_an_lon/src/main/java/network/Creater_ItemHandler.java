package network;

import com.google.gson.reflect.TypeToken;
import Service_.UserService;
import model.Items.Art;
import model.Items.Electronics;
import model.Items.Item;
import model.Items.Vehicle;

import java.io.ObjectOutputStream;
import java.util.Map;

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
            sendResponse(out, "CREATE_ITEM_RESULT", isSuccess);

        } else {
            System.err.println("Dữ liệu gửi lên không phải là Item!");
            sendResponse(out, "CREATE_ITEM_RESULT", false);
        }
    }
}
