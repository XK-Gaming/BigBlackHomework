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
import network.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Map;

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

    // Bộ đếm thời gian ẩn thông báo sau 5 giây
    private PauseTransition delay = new PauseTransition(Duration.seconds(5));

    public void initialize() {
        jComboBox_Role.getItems().setAll(list);
        jComboBox_Role.setValue(list[0]);

        // Đăng ký nhận tin nhắn từ Server
        client.addListener(this);

        // Cấu hình khi hết 5 giây thì ẩn label thông báo
        delay.setOnFinished(event -> errorLabel1.setVisible(false));

        // TỰ ĐỘNG GẮN SỰ KIỆN: Khi gõ phím vào bất kỳ ô nào, ô đó sẽ hết viền đỏ
        username_DK.setOnKeyTyped(event -> resetStyle(username_DK));
        password_DK1.setOnKeyTyped(event -> resetStyle(password_DK1));
        password_DK2.setOnKeyTyped(event -> resetStyle(password_DK2));
        name.setOnKeyTyped(event -> resetStyle(name));
        address.setOnKeyTyped(event -> resetStyle(address));
    }

    private void ensureConnected() throws IOException {
        if (client.isConnected()) return;
        ServerConfig config = loadServerConfig();
        client.connect(config.ip(), config.port());
        if (!client.isConnected()) {
            throw new IOException("Khong the ket noi toi server.");
        }
    }

    private ServerConfig loadServerConfig() {
        String serverIp = "localhost";
        int serverPort = 8080;
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            if (input != null) {
                props.load(input);
                serverIp = props.getProperty("server.ip", serverIp);
                serverPort = Integer.parseInt(props.getProperty("server.port", String.valueOf(serverPort)));
            }
        } catch (Exception e) {
            System.err.println("Khong load duoc client.properties, dung cau hinh mac dinh: " + e.getMessage());
        }
        return new ServerConfig(serverIp, serverPort);
    }

    private record ServerConfig(String ip, int port) {}

    /**
     * Hàm xóa bỏ viền đỏ khi người dùng bắt đầu nhập liệu
     */
    private void resetStyle(TextField field) {
        field.setStyle(null); // Trả style về mặc định của hệ thống
    }

    /**
     * Hàm hiển thị thông báo và tự động ẩn sau 5 giây
     */
    private void showNotification(String message, Color color) {
        errorLabel1.setTextFill(color);
        errorLabel1.setText(message);
        errorLabel1.setVisible(true);

        delay.stop(); // Reset lại bộ đếm nếu bấm liên tục
        delay.play();
    }

    public void setJbutton_TrangChu() throws IOException {
        client.removeListener(this);
        SceneHelper.changeScene(jbuton_TrangChu, "/fxml/LoginView.fxml");
    }

    @FXML
    public void handleRegister_DangKy() {
        // Kiểm tra các trường trống
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
        // 2. Kiểm tra định dạng USERNAME (3-16 ký tự, không dấu, không khoảng trắng)
        String usernameRegex = "^[a-zA-Z0-9_]{3,16}$";
        if (!username_DK.getText().matches(usernameRegex)) {
            showNotification("Username từ 3-16 ký tự, không chứa ký tự đặc biệt/khoảng trắng!", Color.RED);
            username_DK.setStyle("-fx-border-color: red;");
            return;
        }

        // 3. Kiểm tra định dạng HỌ VÀ TÊN (Chỉ chứa chữ cái tiếng Việt và khoảng trắng)
        String nameRegex = "^[\\p{L} ]{2,50}$";
        if (!name.getText().matches(nameRegex)) {
            showNotification("Định dạng Họ và Tên không hợp lệ ", Color.RED);
            name.setStyle("-fx-border-color: red;");
            return;
        }

        // 4. Kiểm tra định dạng EMAIL (Nếu bạn có dùng trường Email)
        if (address != null) {
            String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
            if (!address.getText().matches(emailRegex)) {
                showNotification("Định dạng Email không hợp lệ!", Color.RED);
                address.setStyle("-fx-border-color: red;");
                return;
            }
        }

        // 5. Kiểm tra mật khẩu ĐỘ BẢO MẬT (Tối thiểu 6 ký tự, có cả chữ và số)
        String passwordText = password_DK1.getText();
        if (passwordText.length() < 6 || !passwordText.matches(".*[a-zA-Z].*") || !passwordText.matches(".*[0-9].*")) {
            showNotification("Mật khẩu phải từ 6 ký tự trở lên, bao gồm cả chữ và số!", Color.RED);
            password_DK1.setStyle("-fx-border-color: red;");
            return;
        }

        // Kiểm tra trùng mật khẩu
        if (!(password_DK1.getText().equals(password_DK2.getText()))) {
            showNotification("Mật khẩu nhập lại không khớp!", Color.RED);
            password_DK2.setStyle("-fx-border-color: red;");
            return;
        }

        User user = new User(username_DK.getText(), password_DK1.getText(), name.getText(), address.getText(), UserRole.fromString(jComboBox_Role.getValue()));
        ClientNetworkExecutor.execute(() -> {
            try {
                ensureConnected();
                client.sendCommand(Command.REGISTER, user);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showNotification("Không thể kết nối tới máy chủ!", Color.RED));
            }
        });
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

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
                        username_DK.setStyle("-fx-border-color: red;"); // Đỏ ô tài khoản nếu đã tồn tại
                    } else {
                        showNotification("Đăng ký thất bại, thử lại sau!", Color.RED);
                    }
                });
            }
        }
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");
    }

    public void resetStyle(ActionEvent actionEvent) {
    }
}
