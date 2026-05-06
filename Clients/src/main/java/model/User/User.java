package model.User;


import java.io.Serializable;

/**
 * Thực thể người dùng (User) được truyền qua network (implements {@link Serializable}).
 *
 * <p>NOTE: Trường {@code email} hiện được dùng như "địa chỉ" (address) theo tên getter/setter.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Tài khoản đăng nhập / định danh user. */
    private String username;

    /** Mật khẩu (plain string). NOTE: trong hệ thống thật không nên truyền/luu plain-text như vậy. */
    private String password;

    /** Tên hiển thị. */
    private String name;

    /** Dữ liệu liên hệ (hiện code dùng như địa chỉ). */
    private String email;

    /** Vai trò/phân quyền. */
    private UserRole role;

    /**
     * Precondition: Các field cơ bản hợp lệ (tuỳ ràng buộc hệ thống).
     * Postcondition: Tạo user mới với các thông tin đã cung cấp.
     * NOTE: Dùng cho đăng ký và nhận user từ server.
     * Method returns: đối tượng {@link User} mới.
     */
    public User(String username, String password, String name, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: username.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Precondition: {@code username} hợp lệ.
     * Postcondition: Cập nhật username.
     * Method returns: nothing.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: password.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Precondition: {@code password} hợp lệ.
     * Postcondition: Cập nhật password.
     * Method returns: nothing.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: name.
     */
    public String getName() {
        return name;
    }

    /**
     * Precondition: {@code name} hợp lệ.
     * Postcondition: Cập nhật name.
     * Method returns: nothing.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * NOTE: Method name là getAddress nhưng trả về field {@code email}.
     * Method returns: giá trị address/email.
     */
    public String getAddress() {
        return email;
    }

    /**
     * Precondition: {@code address} có thể null.
     * Postcondition: Cập nhật field {@code email} bằng address truyền vào.
     * NOTE: Đây là naming mismatch trong code hiện tại.
     * Method returns: nothing.
     */
    public void setAddress(String address) {this.email = address;}

    /**
     * Precondition: Không có.
     * Postcondition: Không đổi state.
     * Method returns: role (có thể null).
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * Precondition: {@code role} khác null.
     * Postcondition: Không đổi state.
     * Method returns: Chuỗi tiếng Việt/nhãn hiển thị của role.
     * NOTE: Nếu {@code role} null sẽ NullPointerException.
     */
    public String getRole_toString() {
        if (role.equals(UserRole.ADMIN)) {
            return "Admin";
        }
        if (role.equals(UserRole.SELLER)) {
            return "Người bán";
        }
        if (role.equals(UserRole.BIDDER)) {
            return "Người đấu giá";
        }
        return "";
    }


}
