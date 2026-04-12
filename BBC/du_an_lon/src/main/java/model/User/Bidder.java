package model.User;

public final class Bidder extends User {
    public Bidder(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.BIDDER);
    }

}

