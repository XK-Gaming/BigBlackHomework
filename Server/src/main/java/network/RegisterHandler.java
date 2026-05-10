package network;

import service.UserService;
import model.User.User;
import model.exception.ConflictException;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterHandler extends BaseHandler implements RequestHandler {
    private UserService userService; // Biến để giữ tham chiếu

    // Nhận userService từ HandlerClient truyền sang
    public RegisterHandler(UserService userService) {
        this.userService = userService;
    }
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        User user = (User) payload;
        try {
            Map<String, Object> result = userService.register(user);
            sendResponse(out, Command.REGISTER_RESULT, result);
        } catch (ConflictException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", "EXSITED");
            result.put("message", e.getMessage());
            result.put("errorType", e.getClass().getSimpleName());
            sendResponse(out, Command.REGISTER_RESULT, result);
        }
    }
}