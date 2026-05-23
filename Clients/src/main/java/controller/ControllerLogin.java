package controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;
import network.*;

import java.util.Map;

public class ControllerLogin implements ServerListener {

    private final AuctionClient client = AuctionClient.getInstance();
    public User p1 = null;

    @FXML private AnchorPane Pane1;
    @FXML private Button jbutton_DangKy;
    @FXML private Button jbutton_DangNhap;
    @FXML private PasswordField password;
    @FXML private Label errorLabel;
    @FXML private TextField username;

    public void initialize() {
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);
    }

    // Xử lý chuyển sang màn hình Đăng ký
    @FXML
    public void setJbutton_DangKy() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SignUpView.fxml"));
            Parent root = loader.load();
            Stage window = (Stage) jbutton_DangKy.getScene().getWindow();
            window.setScene(new Scene(root));
        } catch (Exception e) {e.printStackTrace();}
    }

    // Xử lý nút Đăng nhập
    @FXML
    public void handleLogin() {
        if (username.getText().isEmpty() || password.getText().isEmpty()) {
            errorLabel.setText("Điền thông tin bắt buộc!");
            errorLabel.setVisible(true);
            if (username.getText().isEmpty()) {username.setStyle("-fx-border-color: red;");}
            if (password.getText().isEmpty()) {password.setStyle("-fx-border-color: red;");}
            return;
        }

        String this_username = username.getText();
        String this_password = password.getText();
        ClientNetworkExecutor.execute(() -> {
            try {
                client.sendCommand(network.Command.LOGIN, Map.of(
                        "username", this_username,
                        "password", this_password
                ));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    errorLabel.setText("Không thể kết nối tới máy chủ!");
                    errorLabel.setVisible(true);
                });
            }
        });
    }

    // Xóa viền đỏ khi người dùng gõ lại
    @FXML
    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
        errorLabel.setVisible(false);
    }

    // Nhận phản hồi từ Server qua ObjectStream
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        if (Command.LOGIN_RESULT.equals(command)) {
            // Ép kiểu trực tiếp từ Payload
            Map<String, Object> result = (Map<String, Object>) response.payload();
            boolean isSuccess = result.containsKey("success") && (boolean) result.get("success");

            Platform.runLater(() -> {
                if (!isSuccess) {
                    errorLabel.setText("Đăng nhập không thành công");
                    errorLabel.setVisible(true);
                } else {
                    try {
                        p1 = (User) result.get("user");
                        UserSession.setLoggedInUser(p1);
                        if (p1.getRole() == UserRole.BIDDER) {SceneHelper.changeScene(jbutton_DangNhap, "/fxml/BidderView.fxml");}
                        else if (p1.getRole() == UserRole.SELLER) {SceneHelper.changeScene(jbutton_DangNhap, "/fxml/SellerView.fxml");}
                        else{SceneHelper.changeScene(jbutton_DangNhap, "/fxml/AdminView.fxml");}
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}