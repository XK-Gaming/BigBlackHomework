package network;

import model.User.Bidder;
import model.User.User;
import model.exception.ConflictException;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RegisterHandlerTest {

    // Test đăng ký thành công trả payload service.
    @Test
    void registerSuccessReturnsServicePayload() throws Exception {
        FakeUserService userService = new FakeUserService();
        User user = new Bidder("bidder1", "pass123", "Bidder One", "bidder@example.com");
        RegisterHandler handler = new RegisterHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, user);

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.REGISTER_RESULT, packet.command());
        assertEquals("TRUE", payload.get("success"));
        assertSame(user, userService.registeredUser);
    }

    // Test đăng ký trùng trả response đã tồn tại.
    @Test
    void registerConflictReturnsExistingResponse() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.failure = new ConflictException("Username existed.");
        RegisterHandler handler = new RegisterHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler,
                new Bidder("bidder1", "pass123", "Bidder One", "bidder@example.com"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.REGISTER_RESULT, packet.command());
        assertEquals("EXSITED", payload.get("success"));
        assertEquals("ConflictException", payload.get("errorType"));
        assertEquals("Username existed.", payload.get("message"));
    }

    private static final class FakeUserService extends UserService {
        private RuntimeException failure;
        private User registeredUser;

        @Override
        public Map<String, Object> register(User user) {
            this.registeredUser = user;
            if (failure != null) {
                throw failure;
            }
            Map<String, Object> response = new HashMap<>();
            response.put("success", "TRUE");
            return response;
        }
    }
}
