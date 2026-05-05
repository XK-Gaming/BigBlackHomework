package model.auction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.Items.Art;
import model.User.Bidder;
import model.User.Seller;
import model.exception.AuthenticationException;
import model.exception.DuplicateAuctionException;
import model.factory.ItemType;
import support.AuctionTestFixtures;

class AuctionManagerTest {
    private AuctionManager auctionManager;
    private Seller seller;
    private Seller otherSeller;
    private Bidder bidder;

    @BeforeEach
    void setUp() {
        auctionManager = AuctionTestFixtures.newAuctionManager();
        seller = AuctionTestFixtures.seller("SELLER-1", "seller1");
        otherSeller = AuctionTestFixtures.seller("SELLER-2", "seller2");
        bidder = AuctionTestFixtures.bidder("BIDDER-1", "bidder1");
    }

    @Test
    void createAuctionShouldStoreAuctionAndBuildTypedItem() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        assertEquals(auction, auctionManager.findAuctionById("AUCTION-1"));
        assertTrue(auction.getItem() instanceof Art);
        assertEquals("Van Gogh", ((Art) auction.getItem()).getArtist());
    }

    @Test
    void createAuctionShouldRejectDuplicateAuctionId() {
        auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        assertThrows(
                DuplicateAuctionException.class,
                () -> auctionManager.createAuction(
                        "AUCTION-1",
                        "ITEM-2",
                        seller,
                        ItemType.ART,
                        "Sketch",
                        "Charcoal",
                        90.0,
                        "Monet",
                        Instant.now().minusSeconds(3_600),
                        Instant.now().plusSeconds(3_600)
                )
        );
    }

    @Test
    void startAuctionShouldMoveAuctionToRunningWhenTimeWindowIsOpen() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(300),
                Instant.now().plusSeconds(3_600)
        );

        auctionManager.startAuction(auction.getId());

        assertEquals(AuctionStatus.RUNNING, auction.getStatus());
    }

    @Test
    void placeBidShouldDelegateToAuction() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        auctionManager.placeBid(auction.getId(), bidder, 150.0);

        assertEquals(150.0, auction.getItem().getCurrentHighestPrice());
        assertEquals(bidder, auction.getLeadingBidder());
    }

    @Test
    void watchAuctionShouldCreateInitialSnapshotForBidder() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        auctionManager.watchAuction(auction.getId(), bidder);

        Map<String, Object> snapshot = bidder.getAuctionSnapshot(auction.getId());

        assertNotNull(snapshot);
        assertEquals("Painting", snapshot.get("itemName"));
        assertEquals("RUNNING", snapshot.get("status"));
    }

    @Test
    void cancelAuctionShouldRejectNonOwnerSeller() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        assertThrows(AuthenticationException.class, () -> auctionManager.cancelAuction(auction.getId(), otherSeller));
    }

    @Test
    void cancelAuctionShouldUpdateStatusForOwner() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        auctionManager.cancelAuction(auction.getId(), seller);

        assertEquals(AuctionStatus.CANCELED, auction.getStatus());
    }

    @Test
    void removeAuctionShouldDeleteAuctionFromManager() {
        Auction auction = auctionManager.createAuction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                ItemType.ART,
                "Painting",
                "Oil on canvas",
                100.0,
                "Van Gogh",
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );

        auctionManager.removeAuction(auction.getId(), seller);

        assertNull(auctionManager.findAuctionById(auction.getId()));
    }
}
