package network;

import service.UserService;
import model.User.User;
import java.io.ObjectOutputStream;

// Request thông tin user.
public class GetUserInfoHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetUserInfoHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request thông tin user.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        try {
            String username = (String) payload;
            User user = userService.getUserOnly(username);
            sendResponse(out, Command.GET_USER_INFO_RESULT, user);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
