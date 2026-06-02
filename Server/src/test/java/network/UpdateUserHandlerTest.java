package network;

import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test UpdateUserHandler doc field can update va tra UPDATE_USER_RESULT.
 */
class UpdateUserHandlerTest {

    /**
     * ## Test cap nhat user thanh cong: handler goi service voi dung username/field/value.
     */
    @Test
    void updateUserSuccessReturnsSuccessResponse() throws Exception {
        FakeUserService userService = new FakeUserService(true);
        UpdateUserHandler handler = new UpdateUserHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, Map.of(
                "username", "bidder1",
                "field", "name",
                "value", "New Name"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.UPDATE_USER_RESULT, packet.command());
        assertEquals(true, payload.get("success"));
        assertEquals("bidder1", userService.username);
        assertEquals("name", userService.field);
        assertEquals("New Name", userService.value);
    }

    /**
     * ## Test cap nhat user that bai: service tra false thi handler tra success=false.
     */
    @Test
    void updateUserFailureReturnsFailureResponse() throws Exception {
        UpdateUserHandler handler = new UpdateUserHandler(new FakeUserService(false));

        DataPacket packet = HandlerTestSupport.handle(handler, Map.of(
                "username", "bidder1",
                "field", "unknown",
                "value", "value"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.UPDATE_USER_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
    }

    /**
     * ## Test fake service: dong vai mock UserService cho UpdateUserHandler.
     */
    private static final class FakeUserService extends UserService {
        private final boolean success;
        private String username;
        private String field;
        private String value;

        private FakeUserService(boolean success) {
            this.success = success;
        }

        @Override
        public boolean updateUser(String username, String field, String value) {
            this.username = username;
            this.field = field;
            this.value = value;
            return success;
        }
    }
}
