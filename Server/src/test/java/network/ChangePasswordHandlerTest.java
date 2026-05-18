package network;

import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test ChangePasswordHandler nhan request doi mat khau va tra response ve client.
 */
class ChangePasswordHandlerTest {

    /**
     * ## Test doi mat khau thanh cong: handler goi service dung username/old/new va tra success=true.
     */
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

    /**
     * ## Test doi mat khau that bai: service tra false thi handler tra success=false.
     */
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

    /**
     * ## Test fake service: dong vai mock UserService cho ChangePasswordHandler.
     */
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
