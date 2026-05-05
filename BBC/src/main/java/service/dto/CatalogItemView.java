package service.dto;

import model.Items.Item;

public record CatalogItemView(Item item, String imagePath) {
}
