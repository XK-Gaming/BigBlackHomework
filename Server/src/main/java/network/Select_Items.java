package network;

import model.User.UserRole;
import service.UserService;
import model.Items.Item;

import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class Select_Items extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public Select_Items(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        ArrayList<Item> result;

        // 1. Chuyển đổi payload nhận được từ Client thành String an toàn
        String roleStr = (payload != null) ? String.valueOf(payload).trim() : "";

        try {
            // 2. Nếu chuỗi rỗng (do nút Reset hoặc lỗi truyền tải), mặc định lấy toàn bộ sản phẩm hoặc xử lý an toàn
            if (roleStr.isEmpty()) {
                // Bạn có thể truyền null hoặc một giá trị mặc định tùy thuộc vào logic hàm userService.select_items của bạn
                result = userService.select_items(null);
            } else {
                // 3. Chuyển đổi từ String sang Enum UserRole hợp lệ
                UserRole role = UserRole.valueOf(roleStr.toUpperCase());
                result = userService.select_items(role);
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[Server Lỗi] Không tìm thấy UserRole phù hợp với chuỗi: " + roleStr);
            result = new ArrayList<>(); // Trả về danh sách rỗng để tránh crash hệ thống
        }

        // 4. Trả kết quả về cho Client
        sendResponse(out, Command.SELECT_ITEMS_RESULT, result);
    }
}