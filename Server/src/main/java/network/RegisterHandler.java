package network;

import service.UserService;
import model.User.User;
import model.exception.ConflictException;

import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class RegisterHandler extends BaseHandler implements RequestHandler {
    private final UserService userService; // Biến để giữ tham chiếu

    // Nhận userService từ HandlerClient truyền sang
    public RegisterHandler(UserService userService) {
        this.userService = userService;
    }
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        User user = (User) payload;
         Map<String, Object> response = new HashMap<>();
        // 2. Kiểm tra định dạng USERNAME (3-16 ký tự, không dấu, không khoảng trắng)
        String usernameRegex = "^[a-zA-Z0-9_]{3,16}$";
        if (!user.getUsername().matches(usernameRegex)) {
            response.put("success","FAIL");
            sendResponse(out, Command.REGISTER_RESULT, response);
            return;
        }

        // 3. Kiểm tra định dạng HỌ VÀ TÊN (Chỉ chứa chữ cái tiếng Việt và khoảng trắng)
        String nameRegex = "^[\\p{L} ]{2,50}$";
        if (!user.getName().matches(nameRegex)) {
            response.put("success","FAIL");
            sendResponse(out, Command.REGISTER_RESULT, response);
            return;
        }

        // 4. Kiểm tra định dạng EMAIL (Nếu bạn có dùng trường Email)
        if (user.getAddress() != null) {
            String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
            if (!user.getAddress().matches(emailRegex)) {
                response.put("success","FAIL");
                sendResponse(out, Command.REGISTER_RESULT, response);
                return;
            }}
        else {
            response.put("success","FAIL");
            sendResponse(out, Command.REGISTER_RESULT, response);
            return;
        }

        // 5. Kiểm tra mật khẩu ĐỘ BẢO MẬT (Tối thiểu 6 ký tự, có cả chữ và số)
        String passwordText = user.getPassword();
        if (passwordText.length() < 6 || !passwordText.matches(".*[a-zA-Z].*") || !passwordText.matches(".*[0-9].*")) {
            response.put("success","FAIL");
            sendResponse(out, Command.REGISTER_RESULT, response);
            return;

        }
        Map<String, Object> result = userService.register(user);
        sendResponse(out, Command.REGISTER_RESULT, result);

    }
}