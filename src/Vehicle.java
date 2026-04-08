public class Vehicle extends Item{
    public String model;

    public Vehicle(String id, Seller seller, String name, String description, double startingPrice, String model){
        super(id, seller, name, description, startingPrice);
        setModel(model);
    }

    public void setModel(String model) {
        if (model == null || model.isBlank()){
            throw new IllegalArgumentException("model must not be blank.");
        }
        this.model = model;
    }

    @Override
    public String getItemType(){
        return "Vehicle";
    }

    @Override
    public String printInfo(){
        return getItemType() + " | " + getName()
                + " | Giá khởi điểm: " + formatPrice(getStartingPrice())
                + " | Model: " + model
                + " | Seller: " + getSeller().getUsername();
    }
}