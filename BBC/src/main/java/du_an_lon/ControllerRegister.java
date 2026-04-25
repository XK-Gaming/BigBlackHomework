package du_an_lon;

import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import service.AppServices;
import service.AuthService;
import service.dto.RegistrationRequest;

public class ControllerRegister {
    private final AuthService authService = AppServices.authService();

    @FXML
    private TextField address;

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

    public void initialize() {
        jComboBox_Role.getItems().setAll(authService.getSupportedRoleLabels());
        jComboBox_Role.setValue(AuthService.SELLER_ROLE_LABEL);
    }

    public void setJbutton_TrangChu() {
        SceneHelper.changeScene(jbuton_TrangChu, "View1.fxml");
    }

    public void resetStyle(Event event) {
        Object source = event.getSource();
        if (source instanceof Control control) {
            control.setStyle(null);
        }
    }

    public void handleRegister_DangKy() {
        errorLabel1.setVisible(false);
        highlightMissingFields();

        try {
            authService.register(new RegistrationRequest(
                    name.getText(),
                    username_DK.getText(),
                    password_DK1.getText(),
                    password_DK2.getText(),
                    jComboBox_Role.getValue(),
                    address.getText()
            ));
            FxViewUtils.showSuccess(errorLabel1, "Dang ky thanh cong.");
        } catch (RuntimeException exception) {
            FxViewUtils.showError(errorLabel1, exception.getMessage());
        }
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }

    private void highlightMissingFields() {
        markIfBlank(username_DK);
        markIfBlank(password_DK1);
        markIfBlank(password_DK2);
        markIfBlank(name);
        markIfBlank(address);
    }

    private void markIfBlank(Control control) {
        if (control instanceof TextField textField && textField.getText().isBlank()) {
            control.setStyle("-fx-border-color: red;");
        } else if (control instanceof PasswordField passwordField && passwordField.getText().isBlank()) {
            control.setStyle("-fx-border-color: red;");
        }
    }
}
