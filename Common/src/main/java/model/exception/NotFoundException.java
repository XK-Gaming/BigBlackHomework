package model.exception;

public class NotFoundException extends AuctionException {

    private final String resource;

    public NotFoundException(String resource, String message) {
        super(message);
        this.resource = resource;
    }

    public String getResource() {
        return resource;
    }
}
