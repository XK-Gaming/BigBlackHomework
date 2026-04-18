package du_an_lon;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import dao.DAOUser;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;

import java.io.IOException;

public class ControllerLogin {
    public User p1 = null;

    @FXML
    private AnchorPane Pane1;

    @FXML
    private Button jbutton_DangKy;
    public void setJbutton_DangKy() throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource("View2.fxml"));
        Stage window = (Stage) jbutton_DangKy.getScene().getWindow();
        window.setScene(new Scene(root));
    }
    @FXML
    private Button jbutton_DangNhap;

    @FXML
    private PasswordField password;
    @FXML
    private Label errorLabel;
    @FXML
    private TextField username;
    public void handleRegister() throws IOException {
        if (username.getText().isEmpty() || password.getText().isEmpty()) {
            errorLabel.setText("Điền thông tin bắt buộc!");
            errorLabel.setVisible(true);

            if(username.getText().isEmpty()){
            username.setStyle("-fx-border-color: red;");}
            if(password.getText().isEmpty()){
                password.setStyle("-fx-border-color: red;");
            }
        }
        if(username.getText().equals("An") && password.getText().equals("123456")){
            errorLabel.setText("Đăng nhập không thành công!");
            errorLabel.setVisible(true);
        }
        String this_username = username.getText();
        String this_password = password.getText();

        p1 = DAOUser.getInstance().selectByUsername(this_username, this_password);
        if (p1 == null) {

            errorLabel.setText("Đăng nhập không thành công");
            errorLabel.setVisible(true);
}
        else{
            UserSession.setLoggedInUser(p1);
            if(p1.getRole() == UserRole.BIDDER){SceneHelper.changeScene(jbutton_DangNhap, "View3.fxml");}
            if (p1.getRole() == UserRole.SELLER){SceneHelper.changeScene(jbutton_DangNhap, "View3.1.fxml");}
        }
    }
    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
    }


}




