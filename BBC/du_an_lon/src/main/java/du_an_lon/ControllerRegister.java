package du_an_lon;

import dao.DAOUser;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;

import java.io.IOException;

public class ControllerRegister {
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
    public void handleRegister_DangKy() {
        boolean check = true;
        if (username_DK.getText().isEmpty() || password_DK1.getText().isEmpty() || password_DK2.getText().isEmpty() || name.getText().isEmpty() || address.getText().isEmpty()) {
            errorLabel1.setTextFill(Color.RED);
            errorLabel1.setText("Điền thông tin bắt buộc!");
            errorLabel1.setVisible(true);
            check = false;

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
        }
        if (username_DK.getText().equals("An")) {
            errorLabel1.setTextFill(Color.RED);
            errorLabel1.setText("Tài khoản đã tồn tại!");
            errorLabel1.setVisible(true);
            check = false;
        }
        if (!(password_DK1.getText().equals(password_DK2.getText()))) {
            errorLabel1.setTextFill(Color.RED);
            errorLabel1.setText("Sai mật khẩu");
            errorLabel1.setVisible(true);
            password_DK2.setStyle("-fx-border-color: red;");
            check = false;
        }

        if (check) {
            DAOUser.getInstance().Insert((new User(username_DK.getText(), password_DK1.getText(), name.getText(), address.getText(), UserRole.fromString(jComboBox_Role.getValue()))));
            errorLabel1.setTextFill(Color.BLUE);
            errorLabel1.setText("Đăng ký tài khoản thành công");
            errorLabel1.setVisible(true);
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


    }
}




