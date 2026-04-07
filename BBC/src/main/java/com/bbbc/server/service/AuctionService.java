package com.bbbc.server.service;

import com.bbbc.common.dto.AuctionDetailDto;
import com.bbbc.common.dto.AuctionSummaryDto;
import com.bbbc.domain.auction.Auction;
import com.bbbc.domain.auction.AuctionState;
import com.bbbc.domain.auction.AutoBidConfig;
import com.bbbc.domain.auction.BidTransaction;
import com.bbbc.domain.exception.AuctionClosedException;
import com.bbbc.domain.exception.EntityNotFoundException;
import com.bbbc.domain.exception.InvalidBidException;
import com.bbbc.domain.exception.UnauthorizedException;
import com.bbbc.domain.exception.ValidationException;
import com.bbbc.domain.factory.ItemFactory;
import com.bbbc.domain.item.Art;
import com.bbbc.domain.item.Electronics;
import com.bbbc.domain.item.Item;
import com.bbbc.domain.item.ItemType;
import com.bbbc.domain.item.Vehicle;
import com.bbbc.domain.observer.AuctionObserver;
import com.bbbc.domain.user.User;
import com.bbbc.domain.user.UserRole;
import com.bbbc.server.repository.AuctionRepository;
import com.bbbc.server.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuctionService {
    private final AuctionRepository auctionRepository;
    private final UserRepository userRepository;
    private final ItemFactory itemFactory;
    private final Duration antiSnipingWindow = Duration.ofSeconds(15);
    private final Duration antiSnipingExtension = Duration.ofSeconds(30);

    public AuctionService(AuctionRepository auctionRepository, UserRepository userRepository, ItemFactory itemFactory) {
        this.auctionRepository = auctionRepository;
        this.userRepository = userRepository;
        this.itemFactory = itemFactory;
    }

    public Auction createAuction(User actor, Map<String, Object> payload) {
        requireRole(actor, UserRole.SELLER, UserRole.ADMIN);
        Instant startTime = Instant.parse(String.valueOf(payload.get("startTime")));
        Instant endTime = Instant.parse(String.valueOf(payload.get("endTime")));
        if (!endTime.isAfter(startTime)) {
            throw new ValidationException("Auction end time must be after start time");
        }
        Item item = itemFactory.create(
                ItemType.valueOf(String.valueOf(payload.get("itemType")).toUpperCase()),
                String.valueOf(payload.get("name")),
                String.valueOf(payload.get("description")),
                toDouble(payload.get("startingPrice")),
                startTime,
                endTime,
                actor.getId(),
                safeMap(payload.get("attributes"))
        );
        Auction auction = new Auction(UUID.randomUUID().toString(), item);
        return auctionRepository.save(auction);
    }

    public Auction updateAuction(User actor, String auctionId, Map<String, Object> payload) {
        Auction existing = requireAuction(auctionId);
        existing.getLock().lock();
        try {
            requireOwnerOrAdmin(actor, existing.getItem().getSellerId());
            if (!existing.getBidHistory().isEmpty() || existing.getState() != AuctionState.OPEN) {
                throw new ValidationException("Only untouched OPEN auctions can be edited");
            }
            Instant startTime = Instant.parse(String.valueOf(payload.getOrDefault("startTime", existing.getStartTime().toString())));
            Instant endTime = Instant.parse(String.valueOf(payload.getOrDefault("endTime", existing.getEndTime().toString())));
            Item item = itemFactory.create(
                    ItemType.valueOf(String.valueOf(payload.getOrDefault("itemType", existing.getItem().getItemType().name())).toUpperCase()),
                    String.valueOf(payload.getOrDefault("name", existing.getItem().getName())),
                    String.valueOf(payload.getOrDefault("description", existing.getItem().getDescription())),
                    toDouble(payload.getOrDefault("startingPrice", existing.getItem().getStartingPrice())),
                    startTime,
                    endTime,
                    existing.getItem().getSellerId(),
                    mergeAttributes(existing.getItem(), safeMap(payload.get("attributes")))
            );
            Auction replacement = new Auction(existing.getId(), item);
            existing.getObservers().forEach(replacement::registerObserver);
            replacement.setState(existing.getState());
            auctionRepository.save(replacement);
            return replacement;
        } finally {
            existing.getLock().unlock();
        }
    }

    public void deleteAuction(User actor, String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            requireOwnerOrAdmin(actor, auction.getItem().getSellerId());
            if (!auction.getBidHistory().isEmpty()) {
                throw new ValidationException("Auction with bids cannot be deleted");
            }
            auctionRepository.deleteById(auctionId);
        } finally {
            auction.getLock().unlock();
        }
    }

    public List<AuctionSummaryDto> listAuctions() {
        closeExpiredAuctions();
        return auctionRepository.findAll().stream()
                .peek(this::advanceStateByTime)
                .map(AuctionSummaryDto::from)
                .toList();
    }

    public AuctionDetailDto getAuctionDetail(String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            closeIfExpired(auction);
            advanceStateByTime(auction);
            return AuctionDetailDto.from(auction);
        } finally {
            auction.getLock().unlock();
        }
    }

    public AuctionDetailDto placeBid(User actor, String auctionId, double amount) {
        requireRole(actor, UserRole.BIDDER, UserRole.ADMIN);
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            closeIfExpired(auction);
            advanceStateByTime(auction);
            ensureRunning(auction);
            if (amount <= auction.getItem().getCurrentHighestPrice()) {
                throw new InvalidBidException("Bid must be higher than current price");
            }
            acceptBid(auction, actor.getId(), amount, false, "Manual bid accepted");
            applyAntiSniping(auction);
            resolveAutoBids(auction, actor.getId());
            return AuctionDetailDto.from(auction);
        } finally {
            auction.getLock().unlock();
        }
    }

    public AuctionDetailDto registerAutoBid(User actor, String auctionId, double maxBid, double increment) {
        requireRole(actor, UserRole.BIDDER, UserRole.ADMIN);
        if (increment <= 0) {
            throw new ValidationException("Increment must be positive");
        }
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            closeIfExpired(auction);
            advanceStateByTime(auction);
            if (auction.getState() == AuctionState.FINISHED || auction.getState() == AuctionState.PAID || auction.getState() == AuctionState.CANCELED) {
                throw new AuctionClosedException("Auction is already closed");
            }
            if (maxBid <= auction.getItem().getCurrentHighestPrice()) {
                throw new ValidationException("Auto-bid max must be above current price");
            }
            auction.getAutoBidConfigs().put(actor.getId(), new AutoBidConfig(auctionId, actor.getId(), maxBid, increment));
            resolveAutoBids(auction, actor.getId());
            return AuctionDetailDto.from(auction);
        } finally {
            auction.getLock().unlock();
        }
    }

    public AuctionDetailDto markPaid(User actor, String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            requireOwnerOrAdmin(actor, auction.getItem().getSellerId());
            if (auction.getState() != AuctionState.FINISHED) {
                throw new ValidationException("Only FINISHED auctions can be marked as PAID");
            }
            auction.setState(AuctionState.PAID);
            auction.notifyObservers("Auction marked as PAID");
            return AuctionDetailDto.from(auction);
        } finally {
            auction.getLock().unlock();
        }
    }

    public AuctionDetailDto cancelAuction(User actor, String auctionId) {
        Auction auction = requireAuction(auctionId);
        auction.getLock().lock();
        try {
            requireOwnerOrAdmin(actor, auction.getItem().getSellerId());
            if (auction.getState() == AuctionState.PAID) {
                throw new ValidationException("Paid auction cannot be canceled");
            }
            auction.setState(AuctionState.CANCELED);
            auction.notifyObservers("Auction canceled");
            return AuctionDetailDto.from(auction);
        } finally {
            auction.getLock().unlock();
        }
    }

    public void subscribe(String auctionId, AuctionObserver observer) {
        requireAuction(auctionId).registerObserver(observer);
    }

    public void unsubscribe(String auctionId, AuctionObserver observer) {
        auctionRepository.findById(auctionId).ifPresent(auction -> auction.unregisterObserver(observer));
    }

    public void closeExpiredAuctions() {
        auctionRepository.findAll().forEach(auction -> {
            auction.getLock().lock();
            try {
                closeIfExpired(auction);
            } finally {
                auction.getLock().unlock();
            }
        });
    }

    private void closeIfExpired(Auction auction) {
        if (Instant.now().isBefore(auction.getEndTime())) {
            return;
        }
        if (auction.getState() == AuctionState.FINISHED || auction.getState() == AuctionState.PAID || auction.getState() == AuctionState.CANCELED) {
            return;
        }
        auction.setState(AuctionState.FINISHED);
        auction.setWinnerBidderId(auction.getLeaderBidderId());
        auction.notifyObservers("Auction finished");
    }

    private void advanceStateByTime(Auction auction) {
        Instant now = Instant.now();
        if (auction.getState() == AuctionState.OPEN && !now.isBefore(auction.getStartTime())) {
            auction.setState(AuctionState.RUNNING);
        }
    }

    private void ensureRunning(Auction auction) {
        if (auction.getState() != AuctionState.RUNNING) {
            throw new AuctionClosedException("Auction is not running");
        }
    }

    private void acceptBid(Auction auction, String bidderId, double amount, boolean autoGenerated, String message) {
        BidTransaction bid = new BidTransaction(auction.getId(), bidderId, amount, autoGenerated);
        auction.addBid(bid);
        auction.notifyObservers(message);
    }

    private void applyAntiSniping(Auction auction) {
        Duration remaining = Duration.between(Instant.now(), auction.getEndTime());
        if (!remaining.isNegative() && remaining.compareTo(antiSnipingWindow) <= 0) {
            auction.extendEndTime(auction.getEndTime().plus(antiSnipingExtension));
            auction.notifyObservers("Anti-sniping extension applied");
        }
    }

    private void resolveAutoBids(Auction auction, String triggeringBidderId) {
        if (auction.getAutoBidConfigs().isEmpty()) {
            return;
        }
        List<AutoBidConfig> rankedConfigs = auction.getAutoBidConfigs().values().stream()
                .sorted(Comparator.comparingDouble(AutoBidConfig::getMaxBid).reversed()
                        .thenComparing(AutoBidConfig::getCreatedAt))
                .toList();
        AutoBidConfig best = rankedConfigs.getFirst();
        double currentPrice = auction.getItem().getCurrentHighestPrice();
        double secondCap = currentPrice;
        for (AutoBidConfig config : rankedConfigs) {
            if (!config.getBidderId().equals(best.getBidderId())) {
                secondCap = Math.max(secondCap, config.getMaxBid());
                break;
            }
        }
        if (auction.getLeaderBidderId() != null && !auction.getLeaderBidderId().equals(best.getBidderId())) {
            secondCap = Math.max(secondCap, currentPrice);
        }
        double proposedAmount = Math.min(best.getMaxBid(), secondCap + best.getIncrement());
        if (auction.getLeaderBidderId() == null) {
            proposedAmount = Math.max(currentPrice, Math.min(best.getMaxBid(), currentPrice));
        }
        if (proposedAmount <= currentPrice) {
            return;
        }
        if (best.getBidderId().equals(triggeringBidderId) && best.getBidderId().equals(auction.getLeaderBidderId())) {
            return;
        }
        acceptBid(auction, best.getBidderId(), proposedAmount, true, "Auto-bid updated the leading price");
    }

    private Auction requireAuction(String auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new EntityNotFoundException("Auction not found"));
    }

    private void requireOwnerOrAdmin(User actor, String sellerId) {
        if (actor.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!actor.getId().equals(sellerId)) {
            throw new UnauthorizedException("You are not allowed to manage this auction");
        }
    }

    private void requireRole(User actor, UserRole... roles) {
        for (UserRole role : roles) {
            if (actor.getRole() == role) {
                return;
            }
        }
        throw new UnauthorizedException("User role is not allowed for this action");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Map<String, Object> mergeAttributes(Item existingItem, Map<String, Object> inputAttributes) {
        if (!inputAttributes.isEmpty()) {
            return inputAttributes;
        }
        if (existingItem instanceof Electronics electronics) {
            return Map.of(
                    "brand", electronics.getBrand(),
                    "model", electronics.getModel()
            );
        }
        if (existingItem instanceof Art art) {
            return Map.of("artist", art.getArtist());
        }
        if (existingItem instanceof Vehicle vehicle) {
            return Map.of(
                    "manufacturer", vehicle.getManufacturer(),
                    "year", vehicle.getYear()
            );
        }
        return Map.of();
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
