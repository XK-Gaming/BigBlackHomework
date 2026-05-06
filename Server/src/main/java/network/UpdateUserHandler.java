package network;

import service.UserService;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý request UPDATE_USER.
 *
 * Payload mong đợi: Map chứa username, field và value.
 */
public class UpdateUserHandler extends BaseHandler implements RequestHandler {
    private UserService userService;

    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể cập nhật các field profile được hỗ trợ.
     */
    public UpdateUserHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    /**
     * Precondition: payload chứa username, field và value; out còn mở.
     * Postcondition: Gửi UPDATE_USER_RESULT với success flag và message.
     * Method không trả về giá trị.
     * NOTE: Việc lưu thật xuống DB phụ thuộc DAOUser.Update(), hiện method đó chưa hoàn chỉnh.
     */
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
