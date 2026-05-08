package model.User;

import java.io.Serializable;

public final class Seller extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    public Seller(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.SELLER);
    }

}
