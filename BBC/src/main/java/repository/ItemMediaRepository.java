package repository;

public interface ItemMediaRepository {
    void saveImagePath(String itemId, String imagePath);

    String findImagePath(String itemId);

    String defaultImagePath();
}
