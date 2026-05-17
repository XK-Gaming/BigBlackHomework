package network;

import model.Items.Item;
import model.Items.ItemType;
import model.auction.Auction;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ## JUnit: test cac handler chi doc du lieu tu service roi tra list/object ve client.
 */
class ReadOnlyQueryHandlerTest {

    /**
     * ## Test Select_Items: handler tra SELECT_ITEMS_RESULT voi list item service cung cap.
     */
    @Test
    void selectItemsReturnsItemsFromService() throws Exception {
        FakeUserService userService = new FakeUserService();
        Item item = item();
        userService.items.add(item);

        DataPacket packet = HandlerTestSupport.handle(new Select_Items(userService), null);

        ArrayList<?> payload = (ArrayList<?>) packet.payload();
        Item restored = (Item) payload.getFirst();
        assertEquals(Command.SELECT_ITEMS_RESULT, packet.command());
        assertEquals(1, payload.size());
        assertEquals(item.getName(), restored.getName());
    }

    /**
     * ## Test GetAuctionHandler: handler doc itemId payload va tra auction service tim thay.
     */
    @Test
    void getAuctionReturnsAuctionFromService() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auction = new Auction("auction-1", item(), "seller", Instant.now());

        DataPacket packet = HandlerTestSupport.handle(new GetAuctionHandler(userService), 7);

        Auction payload = (Auction) packet.payload();
        assertEquals(Command.GET_AUCTION_RESULT, packet.command());
        assertEquals("7", userService.requestedItemId);
        assertEquals(userService.auction.getId(), payload.getId());
    }

    /**
     * ## Test GetAllAuctionsHandler: handler tra toan bo list auction tu service.
     */
    @Test
    void getAllAuctionsReturnsListFromService() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.auctions = List.of(new Auction("auction-1", item(), "seller", Instant.now()));

        DataPacket packet = HandlerTestSupport.handle(new GetAllAuctionsHandler(userService), null);

        List<?> payload = (List<?>) packet.payload();
        Auction restored = (Auction) payload.getFirst();
        assertEquals(Command.GET_ALL_AUCTIONS_RESULT, packet.command());
        assertEquals(1, payload.size());
        assertEquals(userService.auctions.getFirst().getId(), restored.getId());
    }

    private Item item() {
        return new Item(
                "Item",
                "Description",
                100,
                Instant.now(),
                Instant.now().plusSeconds(60),
                "seller",
                ItemType.ART,
                "image.png");
    }

    /**
     * ## Test fake service: mo phong cac query service khong ghi DB.
     */
    private static final class FakeUserService extends UserService {
        private final ArrayList<Item> items = new ArrayList<>();
        private Auction auction;
        private List<Auction> auctions = List.of();
        private String requestedItemId;

        @Override
        public ArrayList<Item> select_items() {
            return items;
        }

        @Override
        public Auction getAuctionByItemId(String itemId) {
            this.requestedItemId = itemId;
            return auction;
        }

        @Override
        public List<Auction> getAllAuctions() {
            return auctions;
        }
    }
}
