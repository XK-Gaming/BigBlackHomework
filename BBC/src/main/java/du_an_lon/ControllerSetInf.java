package du_an_lon;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import service.AppServices;
import service.ProfileService;

public class ControllerSetInf {
    private final ProfileService profileService = AppServices.profileService();

    @FXML
    private Label j_LabelName;

    @FXML
    private ImageView j_image;

    @FXML
    private Button j_return;

    @FXML
    private Label show_Password;

    @FXML
    private Label show_userName;

    @FXML
    private AnchorPane Pane_CaiDat;

    @FXML
    private Label Pane_ChuyenKhoan;

    @FXML
    private AnchorPane Pane_ThongTinTaiKhoan;

    @FXML
    private AnchorPane Pane_ThanhToan;

    @FXML
    private AnchorPane Pane_DoiMatKhau;

    @FXML
    private Button j_buttonDangXuat;

    public void initialize() {
        User user = profileService.getCurrentUserOrNull();
        if (user != null) {
            show_userName.setText(user.getUsername());
            show_Password.setText(profileService.maskedPassword());
            j_LabelName.setText(user.getFullName());
        }
        hideAllPanes();
        Pane_ThongTinTaiKhoan.setVisible(true);
    }

    @FXML
    void On_MouseClickImg(MouseEvent event) {
    }

    @FXML
    void j_event_return(ActionEvent event) {
        SceneHelper.changeScene(j_return, profileService.homeView(profileService.requireCurrentUser()));
    }

    @FXML
    void j_OnSetName(ActionEvent event) {
    }

    @FXML
    void j_OnSetTel(ActionEvent event) {
    }

    @FXML
    void j_OnbuttonThongTinDangNhap(ActionEvent event) {
        hideAllPanes();
        Pane_ThongTinTaiKhoan.setVisible(true);
    }

    @FXML
    void j_OnbuttonThanhToan(ActionEvent event) {
        hideAllPanes();
        Pane_ThanhToan.setVisible(true);
    }

    @FXML
    void j_OnbuttonDoiMatKhau(ActionEvent event) {
        hideAllPanes();
        Pane_DoiMatKhau.setVisible(true);
    }

    @FXML
    void j_OnbuttonCaiDat(ActionEvent event) {
        hideAllPanes();
        Pane_CaiDat.setVisible(true);
    }

    @FXML
    void j_OnbuttonDangXuat(ActionEvent event) {
        UserSession.cleanUserSession();
        ItemSession.cleanItemSession();
        SceneHelper.changeScene((Node) j_buttonDangXuat, "View1.fxml");
    }

    private void hideAllPanes() {
        Pane_ThongTinTaiKhoan.setVisible(false);
        Pane_ThanhToan.setVisible(false);
        Pane_DoiMatKhau.setVisible(false);
        Pane_CaiDat.setVisible(false);
    }
}
