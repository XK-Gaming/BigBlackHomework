package network;

import model.DepositTransaction;
import service.UserService;
import java.io.ObjectOutputStream;
import java.util.List;

// Request quản lý nạp tiền.
public class DepositManagementHandler extends BaseHandler implements RequestHandler {
    private final UserService userService;

    public DepositManagementHandler(UserService userService) {
        this.userService = userService;
    }

    // Xử lý request quản lý nạp tiền.
    @Override
    public void handle(Object payload, ObjectOutputStream out) {}

    public static class GetPendingHandler extends BaseHandler implements RequestHandler {
        private final UserService userService;
        public GetPendingHandler(UserService userService) { this.userService = userService; }
        // Xử lý request quản lý nạp tiền.
        @Override
        public void handle(Object payload, ObjectOutputStream out) {
            sendResponse(out, Command.GET_PENDING_DEPOSITS_RESULT, userService.getPendingDeposits());
        }
    }

    public static class ApproveHandler extends BaseHandler implements RequestHandler {
        private final UserService userService;
        public ApproveHandler(UserService userService) { this.userService = userService; }
        // Xử lý request quản lý nạp tiền.
        @Override
        public void handle(Object payload, ObjectOutputStream out) {
            java.util.Map<String, String> data = (java.util.Map<String, String>) payload;
            String username = data.get("username");
            boolean success = userService.approveDeposit(username, data.get("transactionId"));
            sendResponse(out, Command.APPROVE_DEPOSIT_RESULT, success);

            if (success) {

                AuctionServer.sendToSpecificUser(username, Command.NOTIFICATION, "Yêu cầu nạp tiền của bạn đã được phê duyệt!");
                AuctionServer.sendToSpecificUser(username, Command.GET_USER_INFO_RESULT, userService.getUserOnly(username));
            }
        }
    }

    public static class RejectHandler extends BaseHandler implements RequestHandler {
        private final UserService userService;
        public RejectHandler(UserService userService) { this.userService = userService; }
        // Xử lý request quản lý nạp tiền.
        @Override
        public void handle(Object payload, ObjectOutputStream out) {
            java.util.Map<String, String> data = (java.util.Map<String, String>) payload;
            String username = data.get("username");
            boolean success = userService.rejectDeposit(username, data.get("transactionId"));
            sendResponse(out, Command.REJECT_DEPOSIT_RESULT, success);

            if (success) {

                AuctionServer.sendToSpecificUser(username, Command.NOTIFICATION, "Yêu cầu nạp tiền của bạn đã bị từ chối.");
                AuctionServer.sendToSpecificUser(username, Command.GET_USER_INFO_RESULT, userService.getUserOnly(username));
            }
        }
    }

    public static class DeleteHistoryHandler extends BaseHandler implements RequestHandler {
        private final UserService userService;
        // Thao tác database.
        public DeleteHistoryHandler(UserService userService) { this.userService = userService; }
        // Xử lý request quản lý nạp tiền.
        @Override
        public void handle(Object payload, ObjectOutputStream out) {
            java.util.Map<String, String> data = (java.util.Map<String, String>) payload;
            boolean success = userService.deleteDepositHistory(data.get("username"), data.get("transactionId"));
            sendResponse(out, Command.DELETE_DEPOSIT_HISTORY_RESULT, success);
        }
    }
}
