package service.dto;

import java.time.LocalDate;

import model.User.Seller;

public record CreateListingRequest(
        Seller seller,
        String name,
        String description,
        String startingPriceText,
        LocalDate startDate,
        String startTimeText,
        LocalDate endDate,
        String endTimeText,
        String itemTypeLabel,
        String primaryAttribute,
        String secondaryAttribute,
        String imagePath
) {
}
