package model.User;

import java.io.Serializable;

/**
 * User có vai trò người bán.
 */
public final class Seller extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Precondition: Thông tin đăng nhập và profile của seller được truyền đầy đủ.
     * Postcondition: Tạo User với role SELLER.
     */
    public Seller(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.SELLER);
    }

}
