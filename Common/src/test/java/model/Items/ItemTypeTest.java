package model.Items;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemTypeTest {

    /**
     * ## Test parse enum: fromString chap nhan ten enum tieng Anh khong phan biet hoa thuong.
     */
    @Test
    void fromStringAcceptsEnumNamesCaseInsensitively() {
        assertEquals(ItemType.ART, ItemType.fromString(" art "));
        assertEquals(ItemType.ELECTRONICS, ItemType.fromString("ELECTRONICS"));
        assertEquals(ItemType.VEHICLE, ItemType.fromString("vehicle"));
    }

    /**
     * ## Test parse label UI: fromString chap nhan label hien thi cua Item tren giao dien.
     */
    @Test
    void fromStringAcceptsDisplayLabelsUsedByItemUi() {
        assertEquals(ItemType.ART, ItemType.fromString(labelFor(ItemType.ART)));
        assertEquals(ItemType.ELECTRONICS, ItemType.fromString(labelFor(ItemType.ELECTRONICS)));
        assertEquals(ItemType.VEHICLE, ItemType.fromString(labelFor(ItemType.VEHICLE)));
    }

    /**
     * ## Test parse input khong hop le: null/rong/chuoi la phai tra null thay vi crash.
     */
    @Test
    void fromStringReturnsNullForUnknownOrMissingText() {
        assertNull(ItemType.fromString(null));
        assertNull(ItemType.fromString(""));
        assertNull(ItemType.fromString("collectible"));
    }

    private String labelFor(ItemType type) {
        return new Item(
                "Item",
                "Description",
                1,
                Instant.now(),
                Instant.now().plusSeconds(1),
                "seller",
                type,
                "item.png").getItemType();
    }
}
