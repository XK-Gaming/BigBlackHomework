package domain.item;

import java.time.LocalDateTime;

public class Art extends Item {
    private String artist;

    public Art(
            String id,
            String name,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String artist
    ) {
        super(id, name, description, startingPrice, startTime, endTime);
        setArtist(artist);
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        if (artist == null || artist.isBlank()) {
            throw new IllegalArgumentException("Artist must not be blank.");
        }
        this.artist = artist;
    }

    @Override
    public String getCategory() {
        return "Art";
    }
}
