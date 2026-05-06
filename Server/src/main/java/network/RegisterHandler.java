package network;

import service.UserService;
import model.User.User;

import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * Xử lý request REGISTER.
 *
 * Payload mong đợi: object có dữ liệu user và có thể chuyển về model.User.User.
 */
public class RegisterHandler extends BaseHandler implements RequestHandler {
    private UserService userService; // Biến để giữ tham chiếu

    // Nhận userService từ HandlerClient truyền sang
    /**
     * Precondition: userService đã được khởi tạo.
     * Postcondition: Handler có thể chuyển request đăng ký xuống service layer.
     */
    public RegisterHandler(UserService userService) {
        this.userService = userService;
    }
    @Override
    /**
     * Precondition: payload chứa dữ liệu đăng ký user; out còn mở.
     * Postcondition: Gửi REGISTER_RESULT với map success/message do UserService trả về.
     * Method không trả về giá trị.
     * NOTE: Username đã tồn tại sẽ bị UserService.register() từ chối.
     */
    public void handle(Object payload, ObjectOutputStream out) {
        User user = gson.fromJson(gson.toJson(payload), User.class);
        Map<String,Object> result = userService.register(user);
        sendResponse(out, "REGISTER_RESULT", result);
    }
}
