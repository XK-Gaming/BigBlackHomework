package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import model.User.User;
import model.User.UserSession;
import network.AuctionClient;
import network.DataPacket;
import network.ServerListener;
import java.io.IOException;

import java.util.Map;

/**
 * Controller cho màn hình "Cài đặt / Thông tin tài khoản" (đổi tên, đổi số điện thoại, đổi mật khẩu, logout).
 *
 * <p>Giao tiếp server:
 * <ul>
 *   <li>{@code UPDATE_USER} -> {@code UPDATE_USER_RESULT}</li>
 *   <li>{@code CHANGE_PASSWORD} -> {@code CHANGE_PASSWORD_RESULT}</li>
 *   <li>{@code LOGOUT}</li>
 * </ul>
 */
public class ControllerSetInf implements ServerListener {

    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    @FXML
    private AnchorPane Pane1;

    @FXML
    private Label j_LabelName;

    @FXML
    private ImageView j_image;

    @FXML
    private Button j_return;

    @FXML
    private Label j_textSoDu;

    @FXML
    private Label show_Password;

    @FXML
    private Label show_userName;

    @FXML
    private TextField j_inputNewName;

    @FXML
    private TextField j_inputNewTel;

    @FXML
    private PasswordField j_inputOldPassword;

    @FXML
    private PasswordField j_inputNewPassword;

    @FXML
    private PasswordField j_inputConfirmPassword;

    @FXML
    private Label j_labelMessageName;

    @FXML
    private Label j_labelMessagePassword;

    @FXML
    void On_MouseClickImg(MouseEvent event) {

    }

    /**
     * Precondition: Các trường @FXML đã inject; user đã đăng nhập (có {@link UserSession}).
     * Postcondition: Đăng ký listener, hiển thị username và mask password, hiển thị tên user trên header.
     * NOTE: Hiện code gọi {@code p1.getName()} ngoài block null-check; nếu session null sẽ NullPointerException.
     * Method returns: nothing.
     */
    public void initialize() {
        client.setListener(this);
        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            show_userName.setText(p1.getUsername());
            show_Password.setText("********");
        }
        j_LabelName.setText(p1.getName());

    }
    @FXML
    /**
     * Precondition: {@code j_return} thuộc scene hiện tại.
     * Postcondition: Chuyển về {@code View3.fxml}.
     * Method returns: nothing.
     */
    void j_event_return(ActionEvent event) {
        SceneHelper.changeScene((Node) j_return, "View3.fxml");

    }
        @FXML
        private AnchorPane Pane_CaiDat;

        @FXML
        private Label Pane_ChuyenKhoan;

        @FXML
        private AnchorPane Pane_ThongTinTaiKhoan;

        @FXML
        private AnchorPane Pane_ThanhToan;

        @FXML
        private AnchorPane Pane_ĐoiMatKhau;

        @FXML
        private Button j_buttonCaiDat;

        @FXML
        private Button j_buttonDangXuat;

        @FXML
        private Button j_buttonDoiMatKhau;

        @FXML
        private Button j_buttonThanhToan;

        @FXML
        private Button j_buttonThongTinDangNhap;

        @FXML
        /**
         * Precondition: User đã login; {@code j_inputNewName} có nội dung mới.
         * Postcondition: Nếu tên hợp lệ thì gửi {@code UPDATE_USER} lên server (field=name).
         * NOTE: Nếu input rỗng thì chỉ hiển thị lỗi, không gửi request.
         * Method returns: nothing.
         */
        void j_OnSetName(ActionEvent event)  {
            User p1 = UserSession.getLoggedInUser();
            String newName = j_inputNewName.getText();

            if (newName == null || newName.trim().isEmpty()) {
                j_labelMessageName.setTextFill(Color.RED);
                j_labelMessageName.setText("Tên không được để trống!");
                j_labelMessageName.setVisible(true);
                return;
            }

            try {
                client.sendCommand("UPDATE_USER", Map.of(
                        "username", p1.getUsername(),
                        "field", "name",
                        "value", newName
                ));
            } catch (IOException e) {
                // Hiển thị thông báo lỗi lên giao diện hoặc console
                System.err.println("Lỗi kết nối khi cập nhật tên: " + e.getMessage());
                // Bạn có thể thêm một cái Alert ở đây để báo cho người dùng
            }
        }

        @FXML
        /**
         * Precondition: User đã login; {@code j_inputNewTel} có nội dung (có thể rỗng - hiện chưa validate).
         * Postcondition: Gửi {@code UPDATE_USER} lên server (field=phone).
         * NOTE: Hiện chưa validate số điện thoại; nếu cần có thể kiểm tra regex/độ dài trước khi gửi.
         * Method returns: nothing.
         */
        void j_OnSetTel(ActionEvent event) {
            User p1 = UserSession.getLoggedInUser();
            String newTel = j_inputNewTel.getText();

            try {
                client.sendCommand("UPDATE_USER", Map.of(
                        "username", p1.getUsername(),
                        "field", "phone",
                        "value", newTel
                ));
                System.out.println("Đã gửi yêu cầu cập nhật số điện thoại");
            } catch (IOException e) {
                // Hiển thị lỗi ra console hoặc thông báo cho người dùng
                System.err.println("Lỗi kết nối Server: " + e.getMessage());

            }
        }

    private void hideAllPanes() {
        Pane_ThongTinTaiKhoan.setVisible(false);
        Pane_ThanhToan.setVisible(false);
        Pane_ĐoiMatKhau.setVisible(false);
        Pane_CaiDat.setVisible(false);
    }

    // --- Xử lý sự kiện các nút bấm bên menu trái ---

    @FXML
    /**
     * Precondition: Các pane đã được inject.
     * Postcondition: Hiển thị pane thông tin tài khoản; ẩn các pane khác.
     * Method returns: nothing.
     */
    void j_OnbuttonThongTinDangNhap(ActionEvent event) {
        hideAllPanes();
        Pane_ThongTinTaiKhoan.setVisible(true);
    }

    @FXML
    /**
     * Precondition: Các pane đã được inject.
     * Postcondition: Hiển thị pane thanh toán; ẩn các pane khác.
     * Method returns: nothing.
     */
    void j_OnbuttonThanhToan(ActionEvent event) {
        hideAllPanes();
        Pane_ThanhToan.setVisible(true);
    }

    @FXML
    /**
     * Precondition: Các pane đã được inject.
     * Postcondition: Hiển thị pane đổi mật khẩu; ẩn các pane khác.
     * Method returns: nothing.
     */
    void j_OnbuttonDoiMatKhau(ActionEvent event) {
        hideAllPanes();
        Pane_ĐoiMatKhau.setVisible(true);
    }

    @FXML
    /**
     * Precondition: Các pane đã được inject.
     * Postcondition: Hiển thị pane cài đặt; ẩn các pane khác.
     * Method returns: nothing.
     */
    void j_OnbuttonCaiDat(ActionEvent event) {
        hideAllPanes();
        Pane_CaiDat.setVisible(true);
    }

    @FXML
    /**
     * Precondition: User đã login; server đang kết nối.
     * Postcondition: Gửi {@code LOGOUT} lên server, xoá {@link UserSession} và chuyển về {@code View1.fxml}.
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu gửi logout lỗi (stream/socket).
     */
    void j_OnbuttonDangXuat(ActionEvent event) throws IOException {
        client.sendCommand("LOGOUT", UserSession.getLoggedInUser().getUsername());
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) j_buttonDangXuat, "View1.fxml");
    }

    @FXML
    /**
     * Precondition: User đã login; các trường password đã được nhập.
     * Postcondition:
     * - Nếu validate fail -> hiển thị lỗi.
     * - Nếu pass -> gửi {@code CHANGE_PASSWORD} lên server.
     * NOTE: Validate gồm đủ 3 trường và mật khẩu mới khớp xác nhận.
     * Method returns: nothing.
     */
    void j_OnChangePassword(ActionEvent event) {
        User p1 = UserSession.getLoggedInUser();
        String oldPassword = j_inputOldPassword.getText();
        String newPassword = j_inputNewPassword.getText();
        String confirmPassword = j_inputConfirmPassword.getText();

        if (oldPassword == null || oldPassword.isEmpty() ||
            newPassword == null || newPassword.isEmpty() ||
            confirmPassword == null || confirmPassword.isEmpty()) {
            j_labelMessagePassword.setTextFill(Color.RED);
            j_labelMessagePassword.setText("Vui lòng điền đầy đủ thông tin!");
            j_labelMessagePassword.setVisible(true);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            j_labelMessagePassword.setTextFill(Color.RED);
            j_labelMessagePassword.setText("Mật khẩu mới không khớp!");
            j_labelMessagePassword.setVisible(true);
            return;
        }
        try {
            client.sendCommand("CHANGE_PASSWORD", Map.of(
                    "username", p1.getUsername(),
                    "oldPassword", oldPassword,
                    "newPassword", newPassword
            ));
            // Thông báo cho người dùng (tùy chọn)
            System.out.println("Đã gửi yêu cầu đổi mật khẩu.");
        } catch (IOException e) {
            // Xử lý khi mất kết nối Server
            System.err.println("Không thể gửi yêu cầu đổi mật khẩu: " + e.getMessage());

        }
    }

    @Override
    /**
     * Precondition:
     * - Với {@code UPDATE_USER_RESULT} payload là {@code Map<String,Object>} có keys {@code success}, {@code message}.
     * - Với {@code CHANGE_PASSWORD_RESULT} payload là {@code Map<String,Object>} có keys {@code success}, {@code message}.
     * Postcondition:
     * - Hiển thị thông báo tương ứng trên UI.
     * - Nếu update name thành công -> cập nhật {@link User} trong {@link UserSession} (chỉ field name).
     * - Nếu đổi mật khẩu thành công -> clear các ô password.
     * NOTE: UI update dùng {@code Platform.runLater}.
     * Method returns: nothing.
     */
    public void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("UPDATE_USER_RESULT".equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");

            Platform.runLater(() -> {
                j_labelMessageName.setTextFill(isSuccess ? Color.BLUE : Color.RED);
                j_labelMessageName.setText(message);
                j_labelMessageName.setVisible(true);

                if (isSuccess) {
                    User p1 = UserSession.getLoggedInUser();
                    p1.setName(j_inputNewName.getText());
                }
            });
        }
        else if ("CHANGE_PASSWORD_RESULT".equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");

            Platform.runLater(() -> {
                j_labelMessagePassword.setTextFill(isSuccess ? Color.BLUE : Color.RED);
                j_labelMessagePassword.setText(message);
                j_labelMessagePassword.setVisible(true);

                if (isSuccess) {
                    j_inputOldPassword.clear();
                    j_inputNewPassword.clear();
                    j_inputConfirmPassword.clear();
                }
            });
        }
    }
}
