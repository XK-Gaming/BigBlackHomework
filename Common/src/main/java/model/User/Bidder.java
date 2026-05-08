package model.User;

import java.io.Serializable;

public final class Bidder extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    // ✅ Constructor no-arg cho Gson deserialization
    public Bidder() {
        super();
    }

    public Bidder(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.BIDDER);
    }


}

