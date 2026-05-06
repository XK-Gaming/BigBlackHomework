package model.User;

/**
 * User có quyền Admin.
 */
public final class Admin extends User {
    /**
     * Precondition: Thông tin đăng nhập và profile của admin được truyền đầy đủ.
     * Postcondition: Tạo User với role ADMIN.
     */
    public Admin(String username, String password, String name, String email) {
        super(username, password, name, email, UserRole.ADMIN);
    }

}
