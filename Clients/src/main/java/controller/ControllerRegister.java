package controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.User.User;
import model.User.UserRole;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;
import java.util.Map;

// Màn đăng ký.
public class ControllerRegister implements ServerListener {
    private AuctionClient client = AuctionClient.getInstance();
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

    @FXML
    private Label errorLabel1;

    @FXML
    private Button LogOut;

    @FXML
    private ComboBox<String> jComboBox_Role;

    private final String[] list = new String[]{"Người bán", "Người đấu giá"};

    private PauseTransition delay = new PauseTransition(Duration.seconds(5));
    // Khởi tạo màn hình.
    public void initialize() {
        jComboBox_Role.getItems().setAll(list);
        jComboBox_Role.setValue(list[0]);

        client.addListener(this);

        delay.setOnFinished(event -> errorLabel1.setVisible(false));

        username_DK.setOnKeyTyped(event -> resetStyle(username_DK));
        password_DK1.setOnKeyTyped(event -> resetStyle(password_DK1));
        password_DK2.setOnKeyTyped(event -> resetStyle(password_DK2));
        name.setOnKeyTyped(event -> resetStyle(name));
        address.setOnKeyTyped(event -> resetStyle(address));
    }
    // Reset trạng thái.
    private void resetStyle(TextField field) {
        field.setStyle(null);
    }
    // Hiển thị giao diện.
    private void showNotification(String message, Color color) {
        errorLabel1.setTextFill(color);
        errorLabel1.setText(message);
        errorLabel1.setVisible(true);

        delay.stop();
        delay.play();
    }

    public void setJbutton_TrangChu() throws IOException {
        client.removeListener(this);
        SceneHelper.changeScene(jbuton_TrangChu, "/fxml/LoginView.fxml");
    }

    // Đăng ký tài khoản.
    @FXML
    public void handleRegister_DangKy() throws IOException {

        if (username_DK.getText().isEmpty() || password_DK1.getText().isEmpty() ||
                password_DK2.getText().isEmpty() || name.getText().isEmpty() || address.getText().isEmpty()) {

            showNotification("Điền thông tin bắt buộc!", Color.RED);

            if (username_DK.getText().isEmpty()) username_DK.setStyle("-fx-border-color: red;");
            if (password_DK1.getText().isEmpty()) password_DK1.setStyle("-fx-border-color: red;");
            if (password_DK2.getText().isEmpty()) password_DK2.setStyle("-fx-border-color: red;");
            if (name.getText().isEmpty()) name.setStyle("-fx-border-color: red;");
            if (address.getText().isEmpty()) address.setStyle("-fx-border-color: red;");
            return;
        }

        String usernameRegex = "^[a-zA-Z0-9_]{3,16}$";
        if (!username_DK.getText().matches(usernameRegex)) {
            showNotification("Username từ 3-16 ký tự, không chứa ký tự đặc biệt/khoảng trắng!", Color.RED);
            username_DK.setStyle("-fx-border-color: red;");
            return;
        }

        String nameRegex = "^[\\p{L} ]{2,50}$";
        if (!name.getText().matches(nameRegex)) {
            showNotification("Định dạng Họ và Tên không hợp lệ ", Color.RED);
            name.setStyle("-fx-border-color: red;");
            return;
        }

        if (address != null) {
            String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
            if (!address.getText().matches(emailRegex)) {
                showNotification("Định dạng Email không hợp lệ!", Color.RED);
                address.setStyle("-fx-border-color: red;");
                return;
            }
        }

        String passwordText = password_DK1.getText();
        if (passwordText.length() < 6 || !passwordText.matches(".*[a-zA-Z].*") || !passwordText.matches(".*[0-9].*")) {
            showNotification("Mật khẩu phải từ 6 ký tự trở lên, bao gồm cả chữ và số!", Color.RED);
            password_DK1.setStyle("-fx-border-color: red;");
            return;
        }

        if (!(password_DK1.getText().equals(password_DK2.getText()))) {
            showNotification("Mật khẩu nhập lại không khớp!", Color.RED);
            password_DK2.setStyle("-fx-border-color: red;");
            return;
        }

        User user = new User(username_DK.getText(), password_DK1.getText(), name.getText(), address.getText(), UserRole.fromString(jComboBox_Role.getValue()));
        try{
        client.sendCommand(Command.REGISTER, user);}
        catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                showNotification("Không thể kết nối tới máy chủ!", Color.RED);
            });
        }
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // Nhận kết quả đăng ký.
        if (Command.REGISTER_RESULT == command) {
            Object payload = response.payload();
            if (payload instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) payload;
                String isSuccess = String.valueOf(result.get("success"));

                Platform.runLater(() -> {
                    if ("TRUE".equalsIgnoreCase(isSuccess)) {
                        showNotification("Đăng ký thành công", Color.BLUE);
                    } else if ("EXSITED".equalsIgnoreCase(isSuccess)) {
                        showNotification("Tài khoản đã tồn tại", Color.RED);
                        username_DK.setStyle("-fx-border-color: red;");
                    } else {
                        showNotification("Đăng ký thất bại, thử lại sau!", Color.RED);
                    }
                });
            }
        }
    }

    // Đăng xuất.
    @FXML
    void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");
    }
    // Reset trạng thái.
    public void resetStyle(ActionEvent actionEvent) {
    }
}
