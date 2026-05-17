package model.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ## JUnit: test custom exception BidRejectedException dang duoc du an dung cho invalid bid.
 */
class BidRejectedExceptionTest {

    /**
     * ## Test exception: giu dung reason, message va cause de handler tra loi client chinh xac.
     */
    @Test
    void storesReasonMessageAndCause() {
        RuntimeException cause = new RuntimeException("database down");

        BidRejectedException exception = new BidRejectedException(
                BidRejectedException.Reason.PERSIST,
                "Cannot save bid.",
                cause);

        assertTrue(exception instanceof AuctionException);
        assertEquals(BidRejectedException.Reason.PERSIST, exception.getReason());
        assertEquals("Cannot save bid.", exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}
