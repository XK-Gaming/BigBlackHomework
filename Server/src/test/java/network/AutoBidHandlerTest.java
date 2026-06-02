package network;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoBidHandlerTest {

    /**
     * ## Test payload AutoBid khong hop le: handler tra SET_AUTO_BID_RESULT loi thay vi crash server.
     */
    @Test
    void invalidPayloadReturnsAutoBidResultError() throws Exception {
        AutoBidHandler handler = new AutoBidHandler(null);

        DataPacket packet = HandlerTestSupport.handle(handler, "not a map");

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.SET_AUTO_BID_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
        assertEquals(false, payload.get("enabled"));
        assertEquals("IllegalArgumentException", payload.get("errorType"));
    }
}
