package network;

import dao.DAOUser;
import model.User.User;
import service.UserService;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

// Request xóa user.
public class DeleteUserHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public DeleteUserHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request xóa user.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        try {
            String usernameToDelete = (String) payload;
            User userToDelete = DAOUser.getInstance().selectByUsernameOnly(usernameToDelete);

            Map<String, Object> response = new HashMap<>();
            if (userToDelete == null) {
                response.put("success", false);
                response.put("message", "Không tìm thấy người dùng!");
                sendResponse(out, Command.DELETE_USER_RESULT, response);
                return;
            }

            if (userToDelete.getRole().equals(model.User.UserRole.ADMIN)) {
                response.put("success", false);
                response.put("message", "Không thể xóa tài khoản Admin!");
                sendResponse(out, Command.DELETE_USER_RESULT, response);
                return;
            }

            int result = DAOUser.getInstance().Delete(userToDelete);
            if (result > 0) {

                if (AuctionServer.isUserOnline(usernameToDelete)) {
                    AuctionServer.sendToSpecificUser(usernameToDelete, Command.FORCE_LOGOUT, "Tài khoản của bạn đã bị Admin xóa!");
                    AuctionServer.removeOnlineClient(usernameToDelete);
                }

                response.put("success", true);
                response.put("message", "Xóa người dùng thành công!");
                response.put("username", usernameToDelete);
            } else {
                response.put("success", false);
                response.put("message", "Xóa người dùng thất bại!");
            }
            sendResponse(out, Command.DELETE_USER_RESULT, response);
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi: " + e.getMessage());
            sendResponse(out, Command.DELETE_USER_RESULT, errorResponse);
        }
    }
}
