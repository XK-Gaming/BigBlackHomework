package model.Items;

import java.io.File;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class Art extends Item implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String artist;
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

    public String getArtist() {
        return artist;
    }

    public Map<String,String> getProperties(){
        Map<String,String> map = new HashMap<>();
        map.put("description", getDescription());
        map.put("artist", getArtist());
        return map;
    }
}
