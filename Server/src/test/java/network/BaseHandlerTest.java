package network;

import model.exception.BidRejectedException;
import model.exception.PersistenceException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test mapping loi trong BaseHandler ma cac handler server dang ke thua.
 */
class BaseHandlerTest {

    private final TestHandler handler = new TestHandler();

    /**
     * ## Test dat bid thap hon gia hien tai: response phai co reason PRICE_TOO_LOW cho client hien thi.
     */
    @Test
    void bidRejectedExceptionIncludesReasonForClient() {
        Map<String, Object> response = handler.mapError(new BidRejectedException(
                BidRejectedException.Reason.PRICE_TOO_LOW,
                "Gia dat phai cao hon gia hien tai."));

        assertEquals(false, response.get("success"));
        assertEquals("BidRejectedException", response.get("errorType"));
        assertEquals("PRICE_TOO_LOW", response.get("reason"));
        assertEquals("Gia dat phai cao hon gia hien tai.", response.get("message"));
    }

    /**
     * ## Test loi du lieu: AuctionException co cause thi response phai kem causeType de debug.
     */
    @Test
    void auctionExceptionIncludesCauseTypeWhenPresent() {
        Map<String, Object> response = handler.mapError(new PersistenceException(
                "Khong the luu du lieu.",
                new SQLException("db")));

        assertEquals(false, response.get("success"));
        assertEquals("PersistenceException", response.get("errorType"));
        assertEquals("SQLException", response.get("causeType"));
    }

    /**
     * ## Test helper: expose fillErrorResponse trong test ma khong sua code production.
     */
    private static final class TestHandler extends BaseHandler {
        private Map<String, Object> mapError(Throwable throwable) {
            Map<String, Object> response = new HashMap<>();
            fillErrorResponse(response, throwable);
            return response;
        }
    }
}
