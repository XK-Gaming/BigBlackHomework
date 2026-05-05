package du_an_lon;

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
    public void setJbutton_TrangChu() throws IOException {
        SceneHelper.changeScene(jbuton_TrangChu, "View1.fxml");
    }
    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
    }
    @FXML
    private Label errorLabel1;
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


    public void initialize() {
        jComboBox_Role.getItems().setAll(list);

        // Nếu muốn khi mở app lên nó chọn sẵn một cái (không bị trống)
        jComboBox_Role.setValue(list[0]);
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);


    }
}




