package network;

import model.User.User;
import model.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test LoginHandler tra loi loi xac thuc bang custom exception hien co.
 */
class LoginHandlerTest {

    /**
     * ## Test dang nhap sai: UnauthorizedException phai thanh LOGIN_RESULT success=false.
     */
    @Test
    void authenticationFailureIsReturnedAsLoginResultError() throws Exception {
        LoginHandler handler = new LoginHandler(new RejectingUserService(), null);

        DataPacket packet = handle(handler, Map.of(
                "username", "bidder1",
                "password", "wrong"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.LOGIN_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
        assertEquals("UnauthorizedException", payload.get("errorType"));
    }

    /**
     * ## Test helper: doc goi DataPacket de xac minh payload LoginHandler gui ve.
     */
    private DataPacket handle(LoginHandler handler, Object payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            handler.handle(payload, out);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DataPacket) in.readObject();
        }
    }

    /**
     * ## Test fake service: gia lap login that bai de khong phu thuoc MySQL.
     */
    private static final class RejectingUserService extends UserService {
        @Override
        public User loginAndGetUser(String username, String password) {
            throw new UnauthorizedException("Sai ten dang nhap hoac mat khau.");
        }
    }
}
