package model.User;

import java.io.Serializable;

/**
 * Model user dùng chung cho Admin, Seller và Bidder.
 */
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    /** Tên đăng nhập, đồng thời là khóa logic của user. */
    private String username;
    /** Mật khẩu hiện tại; đang lưu/so sánh plain text trong code hiện tại. */
    private String password;
    /** Tên hiển thị của user. */
    private String name;
    /** Email hoặc address tùy luồng UI đang dùng. */
    private String email;
    /** Vai trò của user trong hệ thống. */
    private UserRole role;

    // ✅ Constructor no-arg cho Gson deserialization
    /**
     * Precondition: Không có.
     * Postcondition: Tạo User rỗng để Gson/serializer populate field sau.
     */
    public User() {
    }

    /**
     * Precondition: Các tham số định danh và role của user được truyền đầy đủ.
     * Postcondition: Tạo User với dữ liệu được truyền vào.
     */
    public User(String username, String password, String name, String email, UserRole role) {
        this.username = username;
        this.password = password;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    /** Precondition: User đã được khởi tạo. Postcondition: Method trả về username. */
    public String getUsername() {
        return username;
    }

    /** Precondition: username là tên đăng nhập mới. Postcondition: Cập nhật username. */
    public void setUsername(String username) {
        this.username = username;
    }

    /** Precondition: User đã được khởi tạo. Postcondition: Method trả về password. */
    public String getPassword() {
        return password;
    }

    /** Precondition: password là mật khẩu mới. Postcondition: Cập nhật password. */
    public void setPassword(String password) {
        this.password = password;
    }

    /** Precondition: User đã được khởi tạo. Postcondition: Method trả về name. */
    public String getName() {
        return name;
    }

    /** Precondition: name là tên hiển thị mới. Postcondition: Cập nhật name. */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Precondition: User đã được khởi tạo.
     * Postcondition: Method trả về email field, đang được một số luồng dùng như address.
     */
    public String getAddress() {
        return email;
    }

    /**
     * Precondition: address là giá trị địa chỉ/email mới.
     * Postcondition: Gán giá trị vào field email.
     */
    public void setAddress(String address) {this.email = address;}

    /** Precondition: User đã được khởi tạo. Postcondition: Method trả về role enum. */
    public UserRole getRole() {
        return role;
    }
    /**
     * Precondition: role khác null.
     * Postcondition: Method trả về tên role dạng tiếng Việt/DB.
     * NOTE: Có thể NullPointerException nếu role chưa được set.
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
