package domain.item;

import java.time.LocalDateTime;

public class Vehicle extends Item {
    private String manufacturer;

    public Vehicle(
            String id,
            String name,
            String description,
            double startingPrice,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String manufacturer
    ) {
        super(id, name, description, startingPrice, startTime, endTime);
        setManufacturer(manufacturer);
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        if (manufacturer == null || manufacturer.isBlank()) {
            throw new IllegalArgumentException("Manufacturer must not be blank.");
        }
        this.manufacturer = manufacturer;
    }

    @Override
    public String getCategory() {
        return "Vehicle";
    }
}
