package model.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.User.Bidder;
import model.User.Seller;
import model.exception.AuctionClosedException;
import model.exception.AuctionException;
import model.exception.InvalidBidException;
import support.AuctionTestFixtures;

class AuctionTest {
    private Seller seller;
    private Bidder bidder;
    private Auction runningAuction;

    @BeforeEach
    void setUp() {
        seller = AuctionTestFixtures.seller("SELLER-1", "seller1");
        bidder = AuctionTestFixtures.bidder("BIDDER-1", "bidder1");
        runningAuction = AuctionTestFixtures.auction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                100.0,
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );
    }

    @Test
    void startShouldMoveAuctionToRunningWhenStartWindowIsActive() {
        Auction auction = AuctionTestFixtures.auction(
                "AUCTION-START",
                "ITEM-START",
                seller,
                100.0,
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(3_600)
        );

        auction.start();

        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    void startShouldRejectWhenAuctionHasNotReachedStartTime() {
        Auction auction = AuctionTestFixtures.auction(
                "AUCTION-FUTURE",
                "ITEM-FUTURE",
                seller,
                100.0,
                Instant.now().plusSeconds(3_600),
                Instant.now().plusSeconds(7_200)
        );

        assertThrows(AuctionException.class, auction::start);
    }

    @Test
    void placeBidShouldUpdateHighestPriceLeaderAndHistory() {
        runningAuction.placeBid(bidder, 150.0);

        assertEquals(150.0, runningAuction.getItem().getCurrentHighestPrice());
        assertEquals(bidder, runningAuction.getLeadingBidder());
        assertEquals(1, runningAuction.getBidHistory().size());
        assertEquals(150.0, runningAuction.getBidHistory().getFirst().getAmount());
    }

    @Test
    void placeBidShouldRejectLowerOrEqualAmount() {
        assertThrows(InvalidBidException.class, () -> runningAuction.placeBid(bidder, 100.0));
    }

    @Test
    void placeBidShouldRejectNullBidder() {
        assertThrows(IllegalArgumentException.class, () -> runningAuction.placeBid(null, 150.0));
    }

    @Test
    void placeBidShouldRejectSellerBiddingOnOwnAuction() {
        Bidder sameUserAsSeller = AuctionTestFixtures.bidder(seller.getId(), "shadowSeller");

        assertThrows(InvalidBidException.class, () -> runningAuction.placeBid(sameUserAsSeller, 150.0));
    }

    @Test
    void placeBidShouldRejectWhenAuctionIsNotRunning() {
        Auction futureAuction = AuctionTestFixtures.auction(
                "AUCTION-OPEN",
                "ITEM-OPEN",
                seller,
                100.0,
                Instant.now().plusSeconds(600),
                Instant.now().plusSeconds(3_600)
        );

        assertThrows(AuctionClosedException.class, () -> futureAuction.placeBid(bidder, 150.0));
    }

    @Test
    void getStatusShouldBecomeFinishedWhenEndTimeHasPassed() {
        Auction endedAuction = AuctionTestFixtures.auction(
                "AUCTION-ENDED",
                "ITEM-ENDED",
                seller,
                100.0,
                Instant.now().minusSeconds(7_200),
                Instant.now().minusSeconds(3_600)
        );

        assertEquals(AuctionStatus.FINISHED, endedAuction.getStatus());
    }

    @Test
    void markPaidShouldRequireWinner() {
        runningAuction.finish();

        assertThrows(IllegalStateException.class, runningAuction::markPaid);
    }

    @Test
    void markPaidShouldSetAuctionToPaidAfterFinishWithWinner() {
        runningAuction.placeBid(bidder, 180.0);
        runningAuction.finish();

        runningAuction.markPaid();

        assertEquals(AuctionStatus.PAID, runningAuction.getStatus());
    }

    @Test
    void getWinnerSummaryShouldDescribeWinnerAfterFinish() {
        runningAuction.placeBid(bidder, 200.0);
        runningAuction.finish();

        String summary = runningAuction.getWinnerSummary();

        assertTrue(summary.contains("Bidder bidder1"));
        assertTrue(summary.contains("200.00"));
    }
}
