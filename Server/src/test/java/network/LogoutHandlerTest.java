package network;

import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test LogoutHandler goi service logout va tra LOGOUT_RESULT.
 */
class LogoutHandlerTest {

    /**
     * ## Test logout: username tu request duoc truyen vao service va handler tra success=true.
     */
    @Test
    void logoutCallsServiceAndReturnsSuccess() throws Exception {
        FakeUserService userService = new FakeUserService();
        LogoutHandler handler = new LogoutHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, Map.of("username", "bidder1"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.LOGOUT_RESULT, packet.command());
        assertEquals("bidder1", userService.loggedOutUsername);
        assertEquals(true, payload.get("success"));
    }

    /**
     * ## Test fake service: ghi nhan username logout thay vi chay server/session that.
     */
    private static final class FakeUserService extends UserService {
        private String loggedOutUsername;

        @Override
        public void logout(String username) {
            this.loggedOutUsername = username;
        }
    }
}
