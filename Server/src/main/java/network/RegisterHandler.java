package network;

import service.UserService;
import model.User.User;

import java.io.ObjectOutputStream;
import java.util.Map;

public class RegisterHandler extends BaseHandler implements RequestHandler {
    private UserService userService; // Biến để giữ tham chiếu

    // Nhận userService từ HandlerClient truyền sang
    public RegisterHandler(UserService userService) {
        this.userService = userService;
    }
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        User user = gson.fromJson(gson.toJson(payload), User.class);
        Map<String,Object> result = userService.register(user);
        sendResponse(out, "REGISTER_RESULT", result);
    }
}