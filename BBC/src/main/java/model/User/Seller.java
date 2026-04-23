package model.User;

public class Seller extends User {
    public Seller(String id, String fullName, String username, String password) {
        super(id, fullName, username, password);
    }

    @Override
    public String getRole() {
        return "Seller";
    }
}
