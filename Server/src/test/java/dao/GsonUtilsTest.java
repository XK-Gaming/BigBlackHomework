package dao;

import com.google.gson.Gson;
import model.auction.BidTransaction;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GsonUtilsTest {

    // Test adapter Instant giữ epoch seconds.
    @Test
    void instantAdapterRoundTripsEpochSeconds() {
        Gson gson = GsonUtils.createGson();
        Instant instant = Instant.ofEpochSecond(1_700_000_000L);

        String json = gson.toJson(instant, Instant.class);
        Instant restored = gson.fromJson(json, Instant.class);

        assertEquals("1700000000", json);
        assertEquals(instant, restored);
    }

    // Test adapter Instant xử lý null.
    @Test
    void instantAdapterHandlesNull() {
        Gson gson = GsonUtils.createGson();

        assertEquals("null", gson.toJson(null, Instant.class));
        assertNull(gson.fromJson("null", Instant.class));
    }

    // Test adapter BidTransaction giữ dữ liệu bid.
    @Test
    void bidTransactionAdapterRoundTripsBidData() {
        Gson gson = GsonUtils.createGson();
        BidTransaction bid = new BidTransaction(
                "bid-1",
                "bidder1",
                150,
                Instant.ofEpochSecond(1_700_000_100L));

        String json = gson.toJson(bid, BidTransaction.class);
        BidTransaction restored = gson.fromJson(json, BidTransaction.class);

        assertEquals("bid-1", restored.getId());
        assertEquals("bidder1", restored.getBidder());
        assertEquals(150, restored.getAmount());
        assertEquals(Instant.ofEpochSecond(1_700_000_100L), restored.getBidTime());
    }

    // Test adapter BidTransaction bỏ field lạ.
    @Test
    void bidTransactionAdapterSkipsUnknownFields() {
        Gson gson = GsonUtils.createGson();
        String json = """
                {
                  "id": "bid-2",
                  "Usernamebidder": "bidder2",
                  "amount": 200,
                  "bidTime": 1700000200,
                  "ignored": "value"
                }
                """;

        BidTransaction restored = gson.fromJson(json, BidTransaction.class);

        assertEquals("bid-2", restored.getId());
        assertEquals("bidder2", restored.getBidder());
        assertEquals(200, restored.getAmount());
        assertEquals(Instant.ofEpochSecond(1_700_000_200L), restored.getBidTime());
    }
}
