package network;

import model.Items.Item;
import model.Items.ItemType;
import model.exception.PersistenceException;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CreaterItemHandlerTest {

    // Test tạo sản phẩm thành công.
    @Test
    void createItemSuccessReturnsTrue() throws Exception {
        FakeUserService userService = new FakeUserService();
        Creater_ItemHandler handler = new Creater_ItemHandler(userService);
        Item item = item();

        DataPacket packet = HandlerTestSupport.handle(handler, item);

        assertEquals(Command.CREATE_ITEM_RESULT, packet.command());
        assertEquals(true, packet.payload());
        assertSame(item, userService.createdItem);
    }

    // Test tạo sản phẩm lỗi lưu DB.
    @Test
    void createItemPersistenceFailureReturnsFalse() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.failure = new PersistenceException("Cannot save item.");
        Creater_ItemHandler handler = new Creater_ItemHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, item());

        assertEquals(Command.CREATE_ITEM_RESULT, packet.command());
        assertEquals(false, packet.payload());
    }

    // Test tạo sản phẩm payload sai.
    @Test
    void createItemInvalidPayloadReturnsFalse() throws Exception {
        FakeUserService userService = new FakeUserService();
        Creater_ItemHandler handler = new Creater_ItemHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, "not an item");

        assertEquals(Command.CREATE_ITEM_RESULT, packet.command());
        assertEquals(false, packet.payload());
        assertEquals(0, userService.callCount);
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

    private static final class FakeUserService extends UserService {
        private RuntimeException failure;
        private Item createdItem;
        private int callCount;

        @Override
        public void creater_item(Item item) {
            callCount++;
            this.createdItem = item;
            if (failure != null) {
                throw failure;
            }
        }
    }
}
