package service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import model.User.Bidder;
import model.User.Seller;
import model.auction.Auction;
import model.auction.AuctionManager;
import model.exception.AuctionNotFoundException;
import model.exception.ValidationException;
import repository.memory.InMemoryItemMediaRepository;
import service.dto.AuctionDetailView;
import support.AuctionTestFixtures;

class AuctionServiceTest {
    private AuctionManager auctionManager;
    private InMemoryItemMediaRepository itemMediaRepository;
    private AuctionService auctionService;
    private Seller seller;
    private Bidder bidder;
    private Auction auction;

    @BeforeEach
    void setUp() {
        auctionManager = AuctionTestFixtures.newAuctionManager();
        itemMediaRepository = new InMemoryItemMediaRepository();
        auctionService = new AuctionService(auctionManager, itemMediaRepository);
        seller = AuctionTestFixtures.seller("SELLER-1", "seller1");
        bidder = AuctionTestFixtures.bidder("BIDDER-1", "bidder1");
        auction = AuctionTestFixtures.auction(
                "AUCTION-1",
                "ITEM-1",
                seller,
                100.0,
                Instant.now().minusSeconds(3_600),
                Instant.now().plusSeconds(3_600)
        );
        auctionManager.addAuction(auction);
    }

    @Test
    void placeBidShouldParseFormattedAmountAndReturnUpdatedDetail() {
        itemMediaRepository.saveImagePath(auction.getItem().getId(), "custom/item-1.png");

        AuctionDetailView detailView = auctionService.placeBid(auction.getItem(), bidder, "1,500");

        assertSame(auction, detailView.auction());
        assertEquals(1_500.0, detailView.auction().getItem().getCurrentHighestPrice());
        assertEquals(bidder, detailView.auction().getLeadingBidder());
        assertEquals("custom/item-1.png", detailView.imagePath());
    }

    @Test
    void placeBidShouldRejectBlankAmount() {
        assertThrows(ValidationException.class, () -> auctionService.placeBid(auction.getItem(), bidder, "   "));
    }

    @Test
    void placeBidShouldRejectInvalidAmountText() {
        assertThrows(ValidationException.class, () -> auctionService.placeBid(auction.getItem(), bidder, "abc"));
    }

    @Test
    void placeBidShouldRejectMissingItem() {
        assertThrows(AuctionNotFoundException.class, () -> auctionService.placeBid(null, bidder, "150"));
    }

    @Test
    void placeBidShouldRejectMissingBidder() {
        assertThrows(ValidationException.class, () -> auctionService.placeBid(auction.getItem(), null, "150"));
    }

    @Test
    void getAuctionDetailByItemIdShouldReturnSavedImagePath() {
        itemMediaRepository.saveImagePath(auction.getItem().getId(), "custom/detail.png");

        AuctionDetailView detailView = auctionService.getAuctionDetailByItemId(auction.getItem().getId());

        assertSame(auction, detailView.auction());
        assertEquals("custom/detail.png", detailView.imagePath());
    }

    @Test
    void getAuctionDetailByItemShouldRejectNullItem() {
        assertThrows(AuctionNotFoundException.class, () -> auctionService.getAuctionDetailByItem(null));
    }

    @Test
    void findAuctionByItemIdShouldRejectUnknownItemId() {
        AuctionNotFoundException exception =
                assertThrows(AuctionNotFoundException.class, () -> auctionService.findAuctionByItemId("UNKNOWN-ITEM"));

        assertNotNull(exception.getMessage());
    }
}
