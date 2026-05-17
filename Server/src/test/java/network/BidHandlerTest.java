package network;

import model.auction.Auction;
import model.exception.BidRejectedException;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test BidHandler xu ly BID_RESULT ma khong can ket noi MySQL that.
 */
class BidHandlerTest {

    /**
     * ## Test bid hop le: handler tra success=true va newPrice khi service chap nhan gia.
     */
    @Test
    void successfulBidReturnsBidResultPayload() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.acceptedPrice = 150;
        BidHandler handler = new BidHandler(userService);

        DataPacket packet = handle(handler, Map.of(
                "itemId", "7",
                "bidderId", "bidder1",
                "amount", "150"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.BID_RESULT, packet.command());
        assertEquals(true, payload.get("success"));
        assertEquals(150.0, payload.get("newPrice"));
        assertEquals("7", payload.get("itemId"));
    }

    /**
     * ## Test dat bid thap hon gia hien tai: BidRejectedException duoc map thanh reason PRICE_TOO_LOW.
     */
    @Test
    void lowBidExceptionIsMappedToReasonInResponse() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.failure = new BidRejectedException(
                BidRejectedException.Reason.PRICE_TOO_LOW,
                "Gia dat phai cao hon gia hien tai.");
        BidHandler handler = new BidHandler(userService);

        DataPacket packet = handle(handler, Map.of(
                "itemId", "7",
                "bidderId", "bidder1",
                "amount", "90"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.BID_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
        assertEquals("BidRejectedException", payload.get("errorType"));
        assertEquals("PRICE_TOO_LOW", payload.get("reason"));
    }

    /**
     * ## Test input khong hop le: amount khong phai so phai tra success=false thay vi lam crash handler.
     */
    @Test
    void nonNumericBidAmountIsReturnedAsErrorResponse() throws Exception {
        BidHandler handler = new BidHandler(new FakeUserService());

        DataPacket packet = handle(handler, Map.of(
                "itemId", "7",
                "bidderId", "bidder1",
                "amount", "not-a-number"));

        Map<?, ?> payload = (Map<?, ?>) packet.payload();
        assertEquals(Command.BID_RESULT, packet.command());
        assertEquals(false, payload.get("success"));
        assertEquals("NumberFormatException", payload.get("errorType"));
    }

    /**
     * ## Test helper: doc DataPacket tu ObjectOutputStream de kiem tra response server gui ve.
     */
    private DataPacket handle(BidHandler handler, Object payload) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            handler.handle(payload, out);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (DataPacket) in.readObject();
        }
    }

    /**
     * ## Test fake service: mo rong UserService de test handler ma khong cham database.
     */
    private static final class FakeUserService extends UserService {
        private RuntimeException failure;
        private double acceptedPrice;

        @Override
        public double processBid(String itemId, String bidderId, double amount) {
            if (failure != null) {
                throw failure;
            }
            return acceptedPrice;
        }

        @Override
        public Auction getAuctionByItemId(String itemId) {
            return null;
        }
    }
}
