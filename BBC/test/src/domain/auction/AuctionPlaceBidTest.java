package src.domain.auction;

import java.time.Instant;

import domain.auction.Auction;
import domain.auction.BidTransaction;
import domain.item.Art;
import domain.item.Electronics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import domain.exception.AuctionClosedException;
import domain.exception.InvalidBidException;
import domain.item.Item;
import domain.user.Bidder;
import domain.user.Seller;

class AuctionPlaceBidTest {

    private Auction auction;
    private Bidder bidder;
    private Seller seller;

    @BeforeEach
    void setUp() {
        seller = new Seller("1", "John Seller","mmb","12133123");
        bidder = new Bidder("2", "Jane Bidder","mmbelle","313223145");

        Item item = new Art(
                "3", "Laptop", "Gaming laptop",
                1000.0,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                seller.getId(),"DA VINICIOUS"
        );

        auction = new Auction("auction-1", item, seller);
    }

    // ─── Happy path ────────────────────────────────────────────

    @Test
    void placeBid_validAmount_shouldSucceed() {
        auction.placeBid(bidder, 1500.0);

        assertEquals(1, auction.getBidHistory().size());
        assertEquals(bidder, auction.getLeadingBidder());
    }

    @Test
    void placeBid_multipleBids_lastHighestWins() {
        Bidder bidder2 = new Bidder("4", "Bob Bidder","heyo","1321323");

        auction.placeBid(bidder, 1500.0);
        auction.placeBid(bidder2, 2000.0);

        assertEquals(2, auction.getBidHistory().size());
        assertEquals(bidder2, auction.getLeadingBidder());
    }

    @Test
    void placeBid_validAmount_shouldRecordInHistory() {
        auction.placeBid(bidder, 1500.0);

        BidTransaction transaction = auction.getBidHistory().getFirst();
        assertEquals(bidder, transaction.getBidder());
        assertEquals(1500.0, transaction.getAmount());
    }

    // ─── Lỗi về giá trị bid (InvalidBidException) ──────────────

    @Test
    void placeBid_amountEqualToCurrentPrice_shouldThrow() {
        assertThrows(InvalidBidException.class, () ->
                auction.placeBid(bidder, 1000.0) // bằng startingPrice
        );
    }

    @Test
    void placeBid_amountLowerThanCurrentPrice_shouldThrow() {
        assertThrows(InvalidBidException.class, () ->
                auction.placeBid(bidder, 500.0)
        );
    }

    @Test
    void placeBid_negativeAmount_shouldThrow() {
        assertThrows(InvalidBidException.class, () ->
                auction.placeBid(bidder, -100.0)
        );
    }

    @Test
    void placeBid_zeroAmount_shouldThrow() {
        assertThrows(InvalidBidException.class, () ->
                auction.placeBid(bidder, 0)
        );
    }

    @Test
    void placeBid_sellerBidsOnOwnAuction_shouldThrow() {
        //cung id voi seller
        Bidder sellerAsBidder = new Bidder("1", "John Seller","johnhandsome","1eee32321"); // cùng ID với seller

        assertThrows(InvalidBidException.class, () ->
                auction.placeBid(sellerAsBidder, 1500.0)
        );
    }

    // ─── Lỗi về trạng thái phiên (AuctionClosedException) ──────

    @Test
    void placeBid_whenAuctionNotStarted_shouldThrow() {
        Item item = new Electronics(
                "item-2", "Phone", "Smartphone",
                500.0,
                Instant.now().plusSeconds(3600), // chưa bắt đầu
                Instant.now().plusSeconds(7200),
                seller.getId(),"vin"
        );
        Auction notStartedAuction = new Auction("auction-2", item, seller);
        // status = OPEN, chưa gọi start()

        assertThrows(AuctionClosedException.class, () ->
                notStartedAuction.placeBid(bidder, 600.0)
        );
    }

    @Test
    void placeBid_whenAuctionFinished_shouldThrow() {
        auction.finish();

        assertThrows(AuctionClosedException.class, () ->
                auction.placeBid(bidder, 1500.0)
        );
    }

    @Test
    void placeBid_whenAuctionCanceled_shouldThrow() {
        auction.cancel();

        assertThrows(AuctionClosedException.class, () ->
                auction.placeBid(bidder, 1500.0)
        );
    }

    // ─── Lỗi tham số null ───────────────────────────────────────

    @Test
    void placeBid_nullBidder_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () ->
                auction.placeBid(null, 1500.0)
        );
    }
}