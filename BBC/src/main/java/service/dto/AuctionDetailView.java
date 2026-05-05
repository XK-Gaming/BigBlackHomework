package service.dto;

import model.auction.Auction;

public record AuctionDetailView(Auction auction, String imagePath) {
}
