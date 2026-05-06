package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Item loại mỹ thuật.
 */
public final class Art extends Item implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Tên nghệ sĩ/tác giả của tác phẩm. */
    private final String artist;

    /**
     * Precondition: Các tham số mô tả tác phẩm mỹ thuật được truyền đầy đủ.
     * Postcondition: Tạo Art item với ItemType.ART và artist riêng.
     */
    public Art(
            String name,
            String description,
            double startingPrice,
            Instant auctionStartTime,
            Instant auctionEndTime,
            String sellerId,
            String artist,
            String img
    ) {
        super(name, description, startingPrice, auctionStartTime, auctionEndTime, sellerId, ItemType.ART, img);
        this.artist = artist;
    }

    /**
     * Precondition: Art đã được khởi tạo.
     * Postcondition: Method trả về tên nghệ sĩ/tác giả.
     */
    public String getArtist() {
        return artist;
    }

    /**
     * Precondition: Art đã được khởi tạo.
     * Postcondition: Method trả về map thuộc tính riêng để DAO serialize vào description/properties.
     */
    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("artist", getArtist());
        return map;
    }
}
