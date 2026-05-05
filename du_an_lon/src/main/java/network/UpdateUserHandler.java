package network;

import Service_.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class UpdateUserHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    public UpdateUserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        String payloadJson = gson.toJson(payload);
        Map<String, String> data = gson.fromJson(payloadJson, Map.class);

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

        sendResponse(out, "UPDATE_USER_RESULT", response);
    }
}