package repository.memory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import repository.ItemMediaRepository;

public final class InMemoryItemMediaRepository implements ItemMediaRepository {
    private static final String DEFAULT_IMAGE_PATH = "";

    private final Map<String, String> imagePathsByItemId = new ConcurrentHashMap<>();

    @Override
    public void saveImagePath(String itemId, String imagePath) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Item id must not be blank.");
        }
        imagePathsByItemId.put(itemId, normalizePath(imagePath));
    }

    @Override
    public String findImagePath(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return DEFAULT_IMAGE_PATH;
        }
        return imagePathsByItemId.getOrDefault(itemId, DEFAULT_IMAGE_PATH);
    }

    @Override
    public String defaultImagePath() {
        return DEFAULT_IMAGE_PATH;
    }

    private String normalizePath(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            return DEFAULT_IMAGE_PATH;
        }
        return imagePath.trim();
    }
}
