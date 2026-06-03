package model.Items;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemFactoryTest {

    // Test tạo Art giữ thông tin đấu giá và artist.
    @Test
    void createArtPreservesAuctionFieldsAndArtist() {
        Instant start = Instant.parse("2026-05-29T01:00:00Z");
        Instant end = start.plusSeconds(3600);

        Item item = ItemFactory.createItem(
                labelFor(ItemType.ART),
                "Painting",
                "Original work",
                1_000,
                100,
                start,
                end,
                "seller",
                Map.of("artist", "A. Nguyen"),
                "painting.png");

        Art art = assertInstanceOf(Art.class, item);
        assertEquals(ItemType.ART, art.getRawItemType());
        assertEquals(100, art.getMinBid(), 0.001);
        assertEquals(start, art.getAuctionStartTime());
        assertEquals(end, art.getAuctionEndTime());
        assertEquals("A. Nguyen", art.getArtist());
        assertEquals("Original work", art.getProperties().get("description"));
    }

    // Test tạo Electronics giữ brand/model.
    @Test
    void createElectronicsPreservesBrandAndModel() {
        Item item = ItemFactory.createItem(
                labelFor(ItemType.ELECTRONICS),
                "Phone",
                "Flagship",
                700,
                50,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "seller",
                Map.of("brand", "Open", "model", "X1"),
                "phone.png");

        Electronics electronics = assertInstanceOf(Electronics.class, item);
        assertEquals(ItemType.ELECTRONICS, electronics.getRawItemType());
        assertEquals("Open", electronics.getBrand());
        assertEquals("X1", electronics.getModel());
        assertEquals(50, electronics.getMinBid(), 0.001);
    }

    // Test tạo Vehicle giữ hãng/năm.
    @Test
    void createVehiclePreservesManufacturerAndYear() {
        Item item = ItemFactory.createItem(
                labelFor(ItemType.VEHICLE),
                "Bike",
                "Electric bike",
                900,
                80,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                "seller",
                Map.of("manufacturer", "Velo", "year", "2026"),
                "bike.png");

        Vehicle vehicle = assertInstanceOf(Vehicle.class, item);
        assertEquals(ItemType.VEHICLE, vehicle.getRawItemType());
        assertEquals("Velo", vehicle.getManufacturer());
        assertEquals("2026", vehicle.getYear());
        assertEquals(80, vehicle.getMinBid(), 0.001);
    }

    // Test factory chặn loại item không hỗ trợ.
    @Test
    void createItemRejectsUnsupportedType() {
        assertThrows(IllegalArgumentException.class,
                () -> ItemFactory.createItem(
                        "unknown",
                        "Item",
                        "Description",
                        100,
                        Instant.now(),
                        Instant.now().plusSeconds(3600),
                        "seller",
                        Map.of(),
                        "item.png"));
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
