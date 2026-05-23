package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class UpdateUserHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public UpdateUserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        Map<String, String> data = (Map<String, String>) payload;

        String username = data.get("username");
        String field = data.get("field");
        String value = data.get("value");

        Map<String, Object> response = new HashMap<>();

        boolean success = userService.updateUser(username, field, value);

        if (success) {
            response.put("success", true);
            response.put("message", "Cập nhật thông tin thành công");
        } else {
            response.put("success", false);
            response.put("message", "Cập nhật thất bại");
        }

        sendResponse(out, Command.UPDATE_USER_RESULT, response);
    }
}