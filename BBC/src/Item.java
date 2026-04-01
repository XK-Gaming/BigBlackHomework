import java.time.LocalDateTime;

public abstract class Item extends Entity {
    private String name;
    private String description;
    private double startingPrice;
    private double currentHighestPrice;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    protected Item(
            String id,
            String name,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        super(id);
        setName(name);
        setDescription(description);
        setStartingPrice(startingPrice);
        setStartTime(startTime);
        setEndTime(endTime);
        this.currentHighestPrice = startingPrice;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank.");
        }
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank.");
        }
        this.description = description;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(double startingPrice) {
        if (startingPrice <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than 0.");
        }
        this.startingPrice = startingPrice;
    }

    public double getCurrentHighestPrice() {
        return currentHighestPrice;
    }

    public void updateCurrentHighestPrice(double currentHighestPrice) {
        if (currentHighestPrice < startingPrice) {
            throw new IllegalArgumentException("Current highest price must be at least starting price.");
        }
        this.currentHighestPrice = currentHighestPrice;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null.");
        }
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        if (endTime == null || (startTime != null && endTime.isBefore(startTime))) {
            throw new IllegalArgumentException("End time must not be before start time.");
        }
        this.endTime = endTime;
    }

    public abstract String getCategory();

    @Override
    public void printInfo() {
        System.out.println("%s{id='%s', name='%s', startingPrice=%.2f, currentHighestPrice=%.2f, start=%s, end=%s}"
                .formatted(getCategory(), getId(), name, startingPrice, currentHighestPrice, startTime, endTime));
    }
}
