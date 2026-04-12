package trang_chu.du_an_lon;

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
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import trang_chu.du_an_lon.dao.DAOPerson;
import trang_chu.du_an_lon.model.Person;
import trang_chu.du_an_lon.model.UserSession;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.sql.SQLException;

public class Controller {
    public Person p1 = null;

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

        p1 = DAOPerson.getInstance().selectByUsername(this_username, this_password);
        if (p1 == null) {

            errorLabel.setText("Đăng nhập không thành công");
            errorLabel.setVisible(true);
}
        else{
            UserSession.setLoggedInUser(p1);
            SceneHelper.changeScene(jbutton_DangNhap, "View3.fxml");


        }

    }
    @FXML
    private Button jbuton_TrangChu;
    public void setJbutton_TrangChu() throws IOException {
        SceneHelper.changeScene(jbuton_TrangChu, "View1.fxml");
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
            DAOPerson.getInstance().Insert((new Person(username_DK.getText(), password_DK1.getText(), name.getText(), address.getText())));
            errorLabel1.setTextFill(Color.BLUE);
            errorLabel1.setText("Đăng ký tài khoản thành công");
            errorLabel1.setVisible(true);
        }
    }


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


    public void resetStyle(javafx.scene.input.KeyEvent keyEvent) {
        TextField field = (TextField) keyEvent.getSource();
        field.setStyle(null);
    }

    @FXML
    private Label j_textSoDu;

    @FXML
    private Label j_LabelName;
    @FXML
    private ImageView j_image;

    public void initialize() {
        Person p1 = UserSession.getLoggedInUser();
        if (j_LabelName != null && p1 != null) {
            j_LabelName.setText(p1.getName());
        }

    }

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }
    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }
    @FXML
    private Button LogOut;

}




