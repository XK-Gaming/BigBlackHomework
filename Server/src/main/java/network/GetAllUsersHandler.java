package network;

import model.User.User;
import service.UserService;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

// Request lấy toàn bộ user.
public class GetAllUsersHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public GetAllUsersHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request lấy toàn bộ user.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {
        try {
            ArrayList<User> allUsers = dao.DAOUser.getInstance().selectAll();
            for (User user : allUsers) {
                user.setOnline(AuctionServer.isUserOnline(user.getUsername()));
            }
            sendResponse(out, Command.GET_ALL_USERS_RESULT, allUsers);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
