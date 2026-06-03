package controller;

import javafx.animation.PauseTransition;
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
import javafx.util.Duration;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;
import network.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

// Màn đăng nhập.
public class ControllerLogin implements ServerListener {

    private final AuctionClient client = AuctionClient.getInstance();
    private static final String DEFAULT_SERVER_IP = "localhost";
    private static final int DEFAULT_SERVER_PORT = 8080;
    public User p1 = null;

    @FXML private AnchorPane Pane1;
    @FXML private Button jbutton_DangKy;
    @FXML private Button jbutton_DangNhap;
    @FXML private PasswordField password;
    @FXML private Label errorLabel;
    @FXML private TextField username;

    private final PauseTransition errorDelay = new PauseTransition(Duration.seconds(5));
    // Khởi tạo màn hình.
    public void initialize() {

        client.addListener(this);

        errorDelay.setOnFinished(event -> clearAllErrors());

        username.setOnKeyTyped(event -> resetStyle(username));
        password.setOnKeyTyped(event -> resetStyle(password));
    }
    // Hiển thị giao diện.
    private void showError(String message, boolean redUsername, boolean redPassword) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);

        if (redUsername) username.setStyle("-fx-border-color: red;");
        if (redPassword) password.setStyle("-fx-border-color: red;");

        errorDelay.stop();
        errorDelay.play();
    }
    // Xóa dữ liệu hiển thị.
    private void clearAllErrors() {
        errorLabel.setVisible(false);
        username.setStyle(null);
        password.setStyle(null);
    }
    // Reset trạng thái.
    private void resetStyle(TextField field) {
        field.setStyle(null);

        if (username.getStyle() == null && password.getStyle() == null) {
            errorLabel.setVisible(false);
            errorDelay.stop();
        }
    }

    @FXML
    public void setJbutton_DangKy() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SignUpView.fxml"));
            Parent root = loader.load();
            Stage window = (Stage) jbutton_DangKy.getScene().getWindow();
            Scene scene = new Scene(root);
            SceneHelper.applyGlobalStyles(scene);
            client.removeListener(this);
            window.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Đăng nhập.
    @FXML
    public void handleLogin() {
        if (username.getText().isEmpty() || password.getText().isEmpty()) {
            showError("Điền thông tin bắt buộc!", username.getText().isEmpty(), password.getText().isEmpty());
            return;
        }

        String this_username = username.getText();
        String this_password = password.getText();

        ClientNetworkExecutor.execute(() -> {
            try {
                client.addListener(this);
                ensureConnected();
                client.sendCommand(network.Command.LOGIN, Map.of(
                        "username", this_username,
                        "password", this_password
                ));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showError("Không thể kết nối tới máy chủ!", true, true);
                });
            }
        });
    }
    // Đảm bảo kết nối.
    private void ensureConnected() throws IOException {
        if (client.isConnected()) {
            return;
        }

        ServerConfig config = loadServerConfig();
        client.connect(config.ip(), config.port());

        if (!client.isConnected()) {
            throw new IOException("Khong the ket noi toi server.");
        }
    }
    // Đọc cấu hình server.
    private ServerConfig loadServerConfig() {
        String serverIp = DEFAULT_SERVER_IP;
        int serverPort = DEFAULT_SERVER_PORT;

        Properties props = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            if (input != null) {
                props.load(input);
                serverIp = props.getProperty("server.ip", DEFAULT_SERVER_IP);
                serverPort = Integer.parseInt(props.getProperty("server.port", String.valueOf(DEFAULT_SERVER_PORT)));
            }
        } catch (Exception e) {
            System.err.println("Khong load duoc client.properties, dung cau hinh mac dinh: " + e.getMessage());
        }

        return new ServerConfig(serverIp, serverPort);
    }

    private record ServerConfig(String ip, int port) {}

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // Nhận kết quả đăng nhập.
        if (Command.LOGIN_RESULT.equals(command)) {

            Map<String, Object> result = (Map<String, Object>) response.payload();
            boolean isSuccess = result.containsKey("success") && (boolean) result.get("success");

            Platform.runLater(() -> {
                if (!isSuccess) {

                    showError("Đăng nhập không thành công!", true, true);
                } else {
                    try {

                        errorDelay.stop();

                        p1 = (User) result.get("user");
                        UserSession.setLoggedInUser(p1);

                        if (p1.getRole() == UserRole.BIDDER) {
                            client.removeListener(this);
                            SceneHelper.changeScene(jbutton_DangNhap, "/fxml/BidderView.fxml");
                        } else if (p1.getRole() == UserRole.SELLER) {
                            client.removeListener(this);
                            SceneHelper.changeScene(jbutton_DangNhap, "/fxml/SellerView.fxml");
                        } else {
                            client.removeListener(this);
                            SceneHelper.changeScene(jbutton_DangNhap, "/fxml/AdminView.fxml");
                        }
                    } catch (ClassCastException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
