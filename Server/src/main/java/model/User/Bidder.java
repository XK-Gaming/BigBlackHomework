package model.User;

import java.io.Serializable;

/**
 * User có vai trò người đấu giá.
 */
public final class Bidder extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    // ✅ Constructor no-arg cho Gson deserialization
    /**
     * Precondition: Không có.
     * Postcondition: Tạo Bidder rỗng để Gson/serializer populate field sau.
     */
    public Bidder() {
        super();
    }

    /**
     * Precondition: Thông tin đăng nhập và profile của bidder được truyền đầy đủ.
     * Postcondition: Tạo User với role BIDDER.
     */
    public Bidder(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.BIDDER);
    }


}

