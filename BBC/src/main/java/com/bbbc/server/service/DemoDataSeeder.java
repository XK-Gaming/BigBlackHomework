package com.bbbc.server.service;

import com.bbbc.domain.user.User;
import com.bbbc.domain.user.UserRole;

import java.time.Instant;
import java.util.Map;

public final class DemoDataSeeder {
    private DemoDataSeeder() {
    }

    public static void seed(AuthenticationService authenticationService, AuctionService auctionService) {
        User seller = authenticationService.register("seller1", "seller1@bbbc.local", "seller123", UserRole.SELLER);
        authenticationService.register("bidder1", "bidder1@bbbc.local", "bidder123", UserRole.BIDDER);
        authenticationService.register("bidder2", "bidder2@bbbc.local", "bidder123", UserRole.BIDDER);
        authenticationService.register("admin", "admin@bbbc.local", "admin123", UserRole.ADMIN);

        auctionService.createAuction(seller, Map.of(
                "itemType", "ELECTRONICS",
                "name", "Gaming Laptop",
                "description", "RTX laptop for auction",
                "startingPrice", 900.0d,
                "startTime", Instant.now().minusSeconds(30).toString(),
                "endTime", Instant.now().plusSeconds(300).toString(),
                "attributes", Map.of("brand", "MSI", "model", "Stealth 16")
        ));

        auctionService.createAuction(seller, Map.of(
                "itemType", "ART",
                "name", "Watercolor Painting",
                "description", "Original artwork",
                "startingPrice", 120.0d,
                "startTime", Instant.now().minusSeconds(30).toString(),
                "endTime", Instant.now().plusSeconds(180).toString(),
                "attributes", Map.of("artist", "Student Artist")
        ));
    }
}
