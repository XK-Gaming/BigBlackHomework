package model.User;

public final class Seller extends User {
    public Seller(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.SELLER);
    }

}
