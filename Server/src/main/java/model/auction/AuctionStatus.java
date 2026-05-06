package model.auction;

/**
 * Enum biểu diễn trạng thái vòng đời của một phiên đấu giá.
 */
public enum AuctionStatus {
    OPEN,
    RUNNING,
    FINISHED,
    PAID,
    CANCELED
}
