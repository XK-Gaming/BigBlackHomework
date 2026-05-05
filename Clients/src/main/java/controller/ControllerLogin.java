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
import network.AuctionClient;
import network.ClientNetworkExecutor;
import network.DataPacket;
import network.ServerListener;

import java.util.Map;

public class ControllerLogin implements ServerListener {

    private AuctionClient client = AuctionClient.getInstance();
    public User p1 = null;

    @FXML
    private AnchorPane Pane1;
    @FXML
    private Button jbutton_DangKy;
    @FXML
    private Button jbutton_DangNhap;
    @FXML
    private PasswordField password;
    @FXML
    private Label errorLabel;
    @FXML
    private TextField username;

    public void initialize() {
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);
    }

    // Xử lý chuyển sang màn hình Đăng ký
    @FXML
    public void setJbutton_DangKy() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("View2.fxml"));
            Parent root = loader.load();
            Stage window = (Stage) jbutton_DangKy.getScene().getWindow();
            window.setScene(new Scene(root));
        } catch (Exception e) {
            System.err.println("Lỗi nạp file FXML View2: " + e.getMessage());
            e.printStackTrace();
            errorLabel.setText("Không thể mở màn hình đăng ký.");
            errorLabel.setVisible(true);
        }
    }

    // Xử lý nút Đăng nhập
    @FXML
    public void handleRegister() {
        if (username.getText().isEmpty() || password.getText().isEmpty()) {
            errorLabel.setText("Điền thông tin bắt buộc!");
            errorLabel.setVisible(true);

            if (username.getText().isEmpty()) {
                username.setStyle("-fx-border-color: red;");
            }
            if (password.getText().isEmpty()) {
                password.setStyle("-fx-border-color: red;");
            }
            return;
        }

        String this_username = username.getText();
        String this_password = password.getText();

        ClientNetworkExecutor.execute(() -> {
            try {
                client.sendCommand("LOGIN", Map.of(
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
        String command = response.getCommand();

        if ("LOGIN_RESULT".equals(command)) {
            // Ép kiểu trực tiếp từ Payload
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = result.containsKey("success") && (boolean) result.get("success");

            Platform.runLater(() -> {
                if (!isSuccess) {
                    String msg = result.get("message") != null ? (String) result.get("message") : "Đăng nhập không thành công";
                    errorLabel.setText(msg);
                    errorLabel.setVisible(true);
                } else {
                    try {
                        // VÌ DÙNG OBJECT STREAM, TA ÉP KIỂU TRỰC TIẾP LUÔN (KHÔNG CẦN GSON)
                        p1 = (User) result.get("user");

                        UserSession.setLoggedInUser(p1);

                        if (p1 != null && p1.getRole() != null) {
                            if (p1.getRole() == UserRole.BIDDER) {
                                SceneHelper.changeScene(jbutton_DangNhap, "View3.fxml");
                            } else if (p1.getRole() == UserRole.SELLER) {
                                SceneHelper.changeScene(jbutton_DangNhap, "View3.1.fxml");
                            }
                        } else {
                            errorLabel.setText("Lỗi phân quyền tài khoản!");
                            errorLabel.setVisible(true);
                        }
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                        errorLabel.setText("Lỗi định dạng dữ liệu User từ Server.");
                        errorLabel.setVisible(true);
                    }
                }
            });
        }
    }
}