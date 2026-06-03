package network;

import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChangePasswordHandlerTest {

    // Test đổi mật khẩu thành công.
    @Test
    void changePasswordSuccessReturnsSuccessResponse() throws Exception {
        FakeUserService userService = new FakeUserService(true);
        ChangePasswordHandler handler = new ChangePasswordHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, Map.of(
                "username", "bidder1",
                "oldPassword", "old",
                "newPassword", "new"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.CHANGE_PASSWORD_RESULT, packet.command());
        assertEquals(true, payload.get("success"));
        assertEquals("bidder1", userService.username);
        assertEquals("old", userService.oldPassword);
        assertEquals("new", userService.newPassword);
    }

    // Test đổi mật khẩu thất bại.
    @Test
    void changePasswordFailureReturnsFailureResponse() throws Exception {
        ChangePasswordHandler handler = new ChangePasswordHandler(new FakeUserService(false));

        DataPacket packet = HandlerTestSupport.handle(handler, Map.of(
                "username", "bidder1",
                "oldPassword", "wrong",
                "newPassword", "new"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.CHANGE_PASSWORD_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
    }

    private static final class FakeUserService extends UserService {
        private final boolean success;
        private String username;
        private String oldPassword;
        private String newPassword;

        private FakeUserService(boolean success) {
            this.success = success;
        }

        @Override
        public boolean changePassword(String username, String oldPassword, String newPassword) {
            this.username = username;
            this.oldPassword = oldPassword;
            this.newPassword = newPassword;
            return success;
        }
    }
}
