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

    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    /** User sau khi đăng nhập thành công (được lấy từ payload). */
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

    /**
     * Precondition: Các trường @FXML đã được inject bởi JavaFX runtime.
     * Postcondition: Controller này được đăng ký làm {@link ServerListener} hiện tại, để nhận response từ server cho màn hình login.
     * NOTE: Do kiến trúc chỉ có 1 listener active, việc chuyển màn hình cần controller mới gọi {@code client.setListener(this)}.
     * Method returns: nothing.
     */
    public void initialize() {
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);
    }

    /**
     * Precondition: Button {@code jbutton_DangKy} đang nằm trong một Scene/Stage hợp lệ.
     * Postcondition: Scene được chuyển sang {@code View2.fxml} (màn hình đăng ký) nếu load thành công.
     * NOTE: Nếu load FXML thất bại sẽ hiển thị message lỗi trên {@code errorLabel}.
     * Method returns: nothing.
     */
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

    /**
     * Precondition: Người dùng đã nhập {@code username} và {@code password}.
     * Postcondition: Nếu input hợp lệ, một command {@code LOGIN} sẽ được gửi lên server (chạy trên luồng nền).
     * NOTE: Không cập nhật UI trực tiếp trong luồng nền; các thông báo lỗi được chuyển về UI thread bằng {@code Platform.runLater}.
     * Method returns: nothing.
     */
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

    /**
     * Precondition: {@code keyEvent.getSource()} là {@link TextField}.
     * Postcondition: Xoá style viền đỏ của field hiện tại và ẩn {@code errorLabel}.
     * NOTE: Được bind vào event gõ phím để reset trạng thái lỗi khi người dùng sửa input.
     * Method returns: nothing.
     * @throws ClassCastException NOTE: Có thể xảy ra nếu source không phải {@link TextField}.
     */
    @FXML
    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
        errorLabel.setVisible(false);
    }

    /**
     * Precondition: {@code response} được server gửi về; với {@code LOGIN_RESULT} thì payload phải là {@code Map<String,Object>}
     * chứa các khoá tối thiểu: {@code success}, {@code message} (tuỳ), {@code user} (khi success).
     * Postcondition: Nếu login fail -> hiển thị lỗi; nếu success -> lưu session và điều hướng đến màn hình theo role.
     * NOTE: UI luôn được cập nhật trong {@code Platform.runLater}.
     * Method returns: nothing.
     * NOTE: Có thể phát sinh {@link ClassCastException} nếu payload không đúng format (đã bắt và hiển thị).
     */
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