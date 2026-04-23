package du_an_lon;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import model.User.User;
import model.User.UserSession;

public class ControllerSetInf {

    @FXML
    private AnchorPane Pane1;

    @FXML
    private Label j_LabelName;

    @FXML
    private ImageView j_image;

    @FXML
    private Button j_return;

    @FXML
    private Label j_textSoDu;

    @FXML
    private Label show_Password;

    @FXML
    private Label show_userName;

    @FXML
    void On_MouseClickImg(MouseEvent event) {

    }
    public void initialize() {
        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            show_userName.setText(p1.getUsername());
            show_Password.setText(p1.getPassword());
        }
        j_LabelName.setText(p1.getName());

    }
    @FXML
    void j_event_return(ActionEvent event) {
        SceneHelper.changeScene((Node) j_return, "View3.fxml");

    }
        @FXML
        private AnchorPane Pane_CaiDat;

        @FXML
        private Label Pane_ChuyenKhoan;

        @FXML
        private AnchorPane Pane_ThongTinTaiKhoan;

        @FXML
        private AnchorPane Pane_ThanhToan;

        @FXML
        private AnchorPane Pane_ĐoiMatKhau;

        @FXML
        private Button j_buttonCaiDat;

        @FXML
        private Button j_buttonDangXuat;

        @FXML
        private Button j_buttonDoiMatKhau;

        @FXML
        private Button j_buttonThanhToan;

        @FXML
        private Button j_buttonThongTinDangNhap;

        @FXML
        void j_OnSetName(ActionEvent event) {

        }

        @FXML
        void j_OnSetTel(ActionEvent event) {

        }
    private void hideAllPanes() {
        Pane_ThongTinTaiKhoan.setVisible(false);
        Pane_ThanhToan.setVisible(false);
        Pane_ĐoiMatKhau.setVisible(false);
        Pane_CaiDat.setVisible(false);
    }

    // --- Xử lý sự kiện các nút bấm bên menu trái ---

    @FXML
    void j_OnbuttonThongTinDangNhap(ActionEvent event) {
        hideAllPanes();
        Pane_ThongTinTaiKhoan.setVisible(true);
    }

    @FXML
    void j_OnbuttonThanhToan(ActionEvent event) {
        hideAllPanes();
        Pane_ThanhToan.setVisible(true); // Hiện pane thanh toán
    }

    @FXML
    void j_OnbuttonDoiMatKhau(ActionEvent event) {
        hideAllPanes();
        Pane_ĐoiMatKhau.setVisible(true);
    }

    @FXML
    void j_OnbuttonCaiDat(ActionEvent event) {
        hideAllPanes();
        Pane_CaiDat.setVisible(true);
    }

    @FXML
    void j_OnbuttonDangXuat(ActionEvent event) {

        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) j_buttonDangXuat, "View1.fxml");
    }



    }
