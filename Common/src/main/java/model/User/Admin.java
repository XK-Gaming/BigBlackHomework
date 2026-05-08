package model.User;

public final class Admin extends User {
    public Admin(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.ADMIN);
    }

}
