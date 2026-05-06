package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import model.User.User;
import model.User.UserRole;
import network.AuctionClient;
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;
import java.util.Map;

/**
 * Controller cho màn hình đăng ký tài khoản.
 *
 * <p>Chức năng chính:
 * <ul>
 *   <li>Validate input đăng ký (username/password/name/address/role).</li>
 *   <li>Gửi command {@code REGISTER} lên server qua {@link network.AuctionClient}.</li>
 *   <li>Nhận {@code REGISTER_RESULT} và hiển thị thông báo lên UI.</li>
 * </ul>
 */
public class ControllerRegister implements ServerListener {
    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    /** Có thể dùng để giữ user sau khi đăng ký (hiện tại không gán). */
    public User p1 = null;

    @FXML
    private TextField address;

    @FXML
    private Button jbuton_GhiNhanDangKy;

    @FXML
    private TextField name;

    @FXML
    private PasswordField password_DK1;

    @FXML
    private PasswordField password_DK2;

    @FXML
    private TextField username_DK;
    @FXML
    private Button jbuton_TrangChu;

    /**
     * Precondition: {@code jbuton_TrangChu} đang thuộc về scene hiện tại.
     * Postcondition: Chuyển về màn {@code View1.fxml} (trang chủ/login).
     * Method returns: nothing.
     * @throws IOException NOTE: Hiện method khai báo throws nhưng {@link SceneHelper#changeScene} tự bắt IOException.
     */
    public void setJbutton_TrangChu() throws IOException {
        SceneHelper.changeScene(jbuton_TrangChu, "View1.fxml");
    }

    /**
     * Precondition: {@code keyEvent.getSource()} là {@link TextField}.
     * Postcondition: Xoá style (viền đỏ) của field đang được nhập.
     * NOTE: Dùng để reset lỗi khi người dùng sửa input.
     * Method returns: nothing.
     * @throws ClassCastException NOTE: Có thể xảy ra nếu source không phải {@link TextField}.
     */
    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
    }
    @FXML
    private Label errorLabel1;

    /**
     * Precondition: Các input cần thiết đã được người dùng nhập.
     * Postcondition: Nếu validate pass thì gửi {@code REGISTER} kèm {@link User} lên server.
     * NOTE: Validate gồm:
     * - không để trống các trường bắt buộc
     * - mật khẩu nhập lại phải trùng nhau
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu ghi stream lỗi khi gửi command.
     */
    public void handleRegister_DangKy() throws IOException{
        if (username_DK.getText().isEmpty() || password_DK1.getText().isEmpty() || password_DK2.getText().isEmpty() || name.getText().isEmpty() || address.getText().isEmpty()) {
            errorLabel1.setTextFill(Color.RED);
            errorLabel1.setText("Điền thông tin bắt buộc!");
            errorLabel1.setVisible(true);

            if (username_DK.getText().isEmpty()) {
                username_DK.setStyle("-fx-border-color: red;");
            }
            if (password_DK1.getText().isEmpty()) {
                password_DK1.setStyle("-fx-border-color: red;");
            }
            if (password_DK2.getText().isEmpty()) {
                password_DK2.setStyle("-fx-border-color: red;");
            }
            if (name.getText().isEmpty()) {
                name.setStyle("-fx-border-color: red;");
            }
            if (address.getText().isEmpty()) {
                address.setStyle("-fx-border-color: red;");
            }
            return;
        }

        if (!(password_DK1.getText().equals(password_DK2.getText()))) {
            errorLabel1.setTextFill(Color.RED);
            errorLabel1.setText("Sai mật khẩu");
            errorLabel1.setVisible(true);
            password_DK2.setStyle("-fx-border-color: red;");
            return;
        }

        User user = new User(username_DK.getText(), password_DK1.getText(), name.getText(), address.getText(), UserRole.fromString(jComboBox_Role.getValue()));
        client.sendCommand("REGISTER", user);
    }

    /**
     * Precondition: Với {@code REGISTER_RESULT} thì payload là {@code Map<String,Object>} có keys:
     * {@code success} (boolean) và {@code message} (String).
     * Postcondition: Hiển thị message đăng ký thành công/thất bại; nếu fail thì đánh dấu lỗi ở {@code password_DK2}.
     * NOTE: UI update dùng {@code Platform.runLater} vì callback có thể chạy trên luồng nền.
     * Method returns: nothing.
     */
    @Override
    public void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("REGISTER_RESULT".equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            String message = (String) result.get("message");
            boolean isSuccess = (boolean) result.get("success");

            Platform.runLater(() -> {
                if (isSuccess) {
                    errorLabel1.setTextFill(Color.BLUE);
                    errorLabel1.setText(message);
                    errorLabel1.setVisible(true);
                } else {
                    errorLabel1.setTextFill(Color.RED);
                    errorLabel1.setText(message);
                    errorLabel1.setVisible(true);
                    password_DK2.setStyle("-fx-border-color: red;");
                }
            });
        }
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }
    @FXML
    private Button LogOut;
    @FXML
    private ComboBox<String> jComboBox_Role;

    private String[] list = new String[]{"Người bán", "Người đấu giá"};


    /**
     * Precondition: Các trường @FXML đã được inject; combobox tồn tại.
     * Postcondition: Cài danh sách role, set giá trị mặc định và đăng ký controller làm listener.
     * NOTE: Việc set role dùng chuỗi tiếng Việt; mapping sang {@link UserRole} thông qua {@link UserRole#fromString(String)}.
     * Method returns: nothing.
     */
    public void initialize() {
        jComboBox_Role.getItems().setAll(list);

        // Nếu muốn khi mở app lên nó chọn sẵn một cái (không bị trống)
        jComboBox_Role.setValue(list[0]);
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);


    }
}




