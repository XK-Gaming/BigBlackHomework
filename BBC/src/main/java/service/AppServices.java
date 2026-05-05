package service;

import java.time.LocalDate;

import model.User.Seller;
import model.auction.AuctionManager;
import repository.ItemMediaRepository;
import repository.UserRepository;
import repository.memory.InMemoryItemMediaRepository;
import repository.memory.InMemoryUserRepository;
import service.dto.CreateListingRequest;

public final class AppServices {
    private static final UserRepository USER_REPOSITORY = new InMemoryUserRepository();
    private static final ItemMediaRepository ITEM_MEDIA_REPOSITORY = new InMemoryItemMediaRepository();

    private static final AuthService AUTH_SERVICE = new AuthService(USER_REPOSITORY);
    private static final SellerService SELLER_SERVICE = new SellerService(AuctionManager.getInstance(), ITEM_MEDIA_REPOSITORY);
    private static final CatalogService CATALOG_SERVICE = new CatalogService(AuctionManager.getInstance(), ITEM_MEDIA_REPOSITORY);
    private static final AuctionService AUCTION_SERVICE = new AuctionService(AuctionManager.getInstance(), ITEM_MEDIA_REPOSITORY);
    private static final ProfileService PROFILE_SERVICE = new ProfileService();

    static {
        seedDemoData();
    }

    private AppServices() {
    }

    public static UserRepository userRepository() {
        return USER_REPOSITORY;
    }

    public static ItemMediaRepository itemMediaRepository() {
        return ITEM_MEDIA_REPOSITORY;
    }

    public static AuthService authService() {
        return AUTH_SERVICE;
    }

    public static SellerService sellerService() {
        return SELLER_SERVICE;
    }

    public static CatalogService catalogService() {
        return CATALOG_SERVICE;
    }

    public static AuctionService auctionService() {
        return AUCTION_SERVICE;
    }

    public static ProfileService profileService() {
        return PROFILE_SERVICE;
    }

    private static void seedDemoData() {
        if (!AuctionManager.getInstance().getAuctions().isEmpty()) {
            return;
        }

        Seller seller = (Seller) USER_REPOSITORY.findByUsername("seller")
                .orElseThrow(() -> new IllegalStateException("Missing default seller."));

        SELLER_SERVICE.createListing(new CreateListingRequest(
                seller,
                "Vintage Painting",
                "Demo art item for bidder screen.",
                "1500000",
                LocalDate.now().minusDays(1),
                "08:00:00",
                LocalDate.now().plusDays(5),
                "23:00:00",
                SellerService.ART_LABEL,
                "Anonymous artist",
                "",
                ITEM_MEDIA_REPOSITORY.defaultImagePath()
        ));

        SELLER_SERVICE.createListing(new CreateListingRequest(
                seller,
                "Gaming Laptop",
                "Demo electronics item for bidder screen.",
                "22000000",
                LocalDate.now().minusDays(1),
                "09:00:00",
                LocalDate.now().plusDays(3),
                "22:00:00",
                SellerService.ELECTRONICS_LABEL,
                "Lenovo",
                "Legion 5",
                ITEM_MEDIA_REPOSITORY.defaultImagePath()
        ));
    }
}
