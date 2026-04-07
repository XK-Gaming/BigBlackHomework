package com.bbbc.server.service;

import com.bbbc.common.dto.AuctionDetailDto;
import com.bbbc.domain.auction.Auction;
import com.bbbc.domain.exception.InvalidBidException;
import com.bbbc.domain.factory.ItemFactory;
import com.bbbc.domain.user.User;
import com.bbbc.domain.user.UserRole;
import com.bbbc.server.repository.InMemoryAuctionRepository;
import com.bbbc.server.repository.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionServiceTest {
    private AuthenticationService authenticationService;
    private AuctionService auctionService;
    private User seller;
    private User bidder1;
    private User bidder2;
    private String auctionId;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        InMemoryAuctionRepository auctionRepository = new InMemoryAuctionRepository();
        authenticationService = new AuthenticationService(userRepository);
        auctionService = new AuctionService(auctionRepository, userRepository, new ItemFactory());

        seller = authenticationService.register("seller", "seller@test.local", "seller123", UserRole.SELLER);
        bidder1 = authenticationService.register("bidder1", "bidder1@test.local", "bidder123", UserRole.BIDDER);
        bidder2 = authenticationService.register("bidder2", "bidder2@test.local", "bidder123", UserRole.BIDDER);

        Auction auction = auctionService.createAuction(seller, Map.of(
                "itemType", "ELECTRONICS",
                "name", "Phone",
                "description", "Gaming phone",
                "startingPrice", 100.0d,
                "startTime", Instant.now().minusSeconds(5).toString(),
                "endTime", Instant.now().plusSeconds(10).toString(),
                "attributes", Map.of("brand", "Asus", "model", "ROG")
        ));
        auctionId = auction.getId();
    }

    @Test
    void rejectsBidLowerThanCurrentPrice() {
        auctionService.placeBid(bidder1, auctionId, 120.0d);

        assertThrows(InvalidBidException.class, () -> auctionService.placeBid(bidder2, auctionId, 119.0d));
    }

    @Test
    void handlesConcurrentBidsWithoutLostUpdate() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            executor.submit(() -> {
                ready.countDown();
                await(start);
                auctionService.placeBid(bidder1, auctionId, 130.0d);
            });
            executor.submit(() -> {
                ready.countDown();
                await(start);
                auctionService.placeBid(bidder2, auctionId, 140.0d);
            });

            ready.await(2, TimeUnit.SECONDS);
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(3, TimeUnit.SECONDS));
        }

        AuctionDetailDto detail = auctionService.getAuctionDetail(auctionId);
        assertEquals(140.0d, detail.currentPrice());
        assertEquals(2, detail.bidHistory().size());
    }

    @Test
    void appliesAutoBidAndAntiSniping() {
        auctionService.registerAutoBid(bidder1, auctionId, 200.0d, 10.0d);
        AuctionDetailDto updated = auctionService.placeBid(bidder2, auctionId, 150.0d);

        assertEquals(String.valueOf(bidder1.getId()), updated.leaderBidderId());
        assertEquals(160.0d, updated.currentPrice());
        assertTrue(Instant.parse(updated.endsAt()).isAfter(Instant.now().plusSeconds(20)));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
