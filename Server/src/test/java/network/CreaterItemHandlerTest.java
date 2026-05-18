package network;

import model.Items.Item;
import model.Items.ItemType;
import model.exception.PersistenceException;
import org.junit.jupiter.api.Test;
import service.UserService;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * ## JUnit: test Creater_ItemHandler tao item qua service va tra CREATE_ITEM_RESULT.
 */
class CreaterItemHandlerTest {

    /**
     * ## Test tao item thanh cong: handler goi service.creater_item va tra true.
     */
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

    /**
     * ## Test tao item loi luu tru: PersistenceException duoc handler map thanh false.
     */
    @Test
    void createItemPersistenceFailureReturnsFalse() throws Exception {
        FakeUserService userService = new FakeUserService();
        userService.failure = new PersistenceException("Cannot save item.");
        Creater_ItemHandler handler = new Creater_ItemHandler(userService);

        DataPacket packet = HandlerTestSupport.handle(handler, item());

        assertEquals(Command.CREATE_ITEM_RESULT, packet.command());
        assertEquals(false, packet.payload());
    }

    /**
     * ## Test payload khong hop le: khong phai Item thi handler tra false va khong goi service.
     */
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

    /**
     * ## Test fake service: dong vai mock UserService cho Creater_ItemHandler.
     */
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
