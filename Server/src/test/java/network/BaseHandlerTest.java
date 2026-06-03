package network;

import model.exception.BidRejectedException;
import model.exception.PersistenceException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BaseHandlerTest {

    private final TestHandler handler = new TestHandler();

    // Test lỗi bid trả reason cho client.
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

    // Test lỗi auction trả loại cause khi có.
    @Test
    void auctionExceptionIncludesCauseTypeWhenPresent() {
        Map<String, Object> response = handler.mapError(new PersistenceException(
                "Khong the luu du lieu.",
                new SQLException("db")));

        assertEquals(false, response.get("success"));
        assertEquals("PersistenceException", response.get("errorType"));
        assertEquals("SQLException", response.get("causeType"));
    }

    private static final class TestHandler extends BaseHandler {
        private Map<String, Object> mapError(Throwable throwable) {
            Map<String, Object> response = new HashMap<>();
            fillErrorResponse(response, throwable);
            return response;
        }
    }
}
