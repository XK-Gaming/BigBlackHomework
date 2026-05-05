package du_an_lon;

import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import service.AppServices;
import service.AuthService;
import service.ProfileService;

public class ControllerLogin {
    private final AuthService authService = AppServices.authService();
    private final ProfileService profileService = AppServices.profileService();

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

    public void setJbutton_DangKy() {
        SceneHelper.changeScene(jbutton_DangKy, "View2.fxml");
    }

    public void handleRegister() {
        errorLabel.setVisible(false);
        try {
            User user = authService.login(username.getText(), password.getText());
            UserSession.setLoggedInUser(user);
            ItemSession.cleanItemSession();
            SceneHelper.changeScene(jbutton_DangNhap, profileService.homeView(user));
        } catch (RuntimeException exception) {
            FxViewUtils.showError(errorLabel, exception.getMessage());
        }
    }

    public void resetStyle(Event event) {
        Object source = event.getSource();
        if (source instanceof Control control) {
            control.setStyle(null);
        }
    }
}
