package service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import model.User.Seller;
import model.auction.Auction;
import model.auction.AuctionManager;
import model.exception.ValidationException;
import model.factory.ItemType;
import repository.ItemMediaRepository;
import service.dto.CreateListingRequest;

public final class SellerService {
    public static final String ART_LABEL = "My thuat";
    public static final String ELECTRONICS_LABEL = "Dien tu";
    public static final String VEHICLE_LABEL = "Phuong tien";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AuctionManager auctionManager;
    private final ItemMediaRepository itemMediaRepository;

    public SellerService(AuctionManager auctionManager, ItemMediaRepository itemMediaRepository) {
        if (auctionManager == null) {
            throw new IllegalArgumentException("AuctionManager must not be null.");
        }
        if (itemMediaRepository == null) {
            throw new IllegalArgumentException("ItemMediaRepository must not be null.");
        }
        this.auctionManager = auctionManager;
        this.itemMediaRepository = itemMediaRepository;
    }

    public List<String> getItemTypeLabels() {
        return List.of(ART_LABEL, ELECTRONICS_LABEL, VEHICLE_LABEL);
    }

    public Auction createListing(CreateListingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("CreateListingRequest must not be null.");
        }
        Seller seller = request.seller();
        if (seller == null) {
            throw new ValidationException("Seller is required.");
        }

        String name = requireText(request.name(), "Item name is required.");
        String description = requireText(request.description(), "Description is required.");
        double startingPrice = parsePrice(request.startingPriceText());
        Instant startTime = parseInstant(request.startDate(), request.startTimeText(), "Start time is invalid.");
        Instant endTime = parseInstant(request.endDate(), request.endTimeText(), "End time is invalid.");
        if (!endTime.isAfter(startTime)) {
            throw new ValidationException("End time must be after start time.");
        }

        ItemType itemType = resolveItemType(request.itemTypeLabel());
        String extraInfo = buildExtraInfo(itemType, request.primaryAttribute(), request.secondaryAttribute());

        String auctionId = "auction-" + UUID.randomUUID();
        String itemId = "item-" + UUID.randomUUID();
        Auction auction = auctionManager.createAuction(
                auctionId,
                itemId,
                seller,
                itemType,
                name,
                description,
                startingPrice,
                extraInfo,
                startTime,
                endTime
        );
        itemMediaRepository.saveImagePath(itemId, request.imagePath());
        return auction;
    }

    private ItemType resolveItemType(String itemTypeLabel) {
        String safeItemType = requireText(itemTypeLabel, "Item type is required.");
        return switch (safeItemType) {
            case ART_LABEL -> ItemType.ART;
            case ELECTRONICS_LABEL -> ItemType.ELECTRONICS;
            case VEHICLE_LABEL -> ItemType.VEHICLE;
            default -> throw new ValidationException("Item type is not supported.");
        };
    }

    private String buildExtraInfo(ItemType itemType, String primaryAttribute, String secondaryAttribute) {
        String primary = requireText(primaryAttribute, "Item detail is required.");
        String secondary = secondaryAttribute == null ? "" : secondaryAttribute.trim();

        return switch (itemType) {
            case ART -> primary;
            case ELECTRONICS, VEHICLE -> secondary.isEmpty() ? primary : primary + " - " + secondary;
        };
    }

    private double parsePrice(String priceText) {
        String safeText = requireText(priceText, "Starting price is required.").replace(",", "");
        try {
            double price = Double.parseDouble(safeText);
            if (price <= 0) {
                throw new ValidationException("Starting price must be greater than 0.");
            }
            return price;
        } catch (NumberFormatException exception) {
            throw new ValidationException("Starting price is invalid.");
        }
    }

    private Instant parseInstant(LocalDate date, String timeText, String message) {
        if (date == null) {
            throw new ValidationException(message);
        }
        try {
            LocalTime time = LocalTime.parse(requireText(timeText, message), TIME_FORMATTER);
            return LocalDateTime.of(date, time)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException exception) {
            throw new ValidationException(message);
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(message);
        }
        return value.trim();
    }
}
