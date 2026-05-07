package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;

import java.io.IOException;
import java.text.DecimalFormat;

public class ControllerPayment {
    User p1 = UserSession.getLoggedInUser();
    Item item1 = ItemSession.getLoggedInItem();
    public void initialize() throws IOException {

                // Vẫn hiển thị thông tin cơ bản của sản phẩm
                j_LabelName.setText(p1.getName());
                j_name.setText(item1.getName());
                renderImage();
                j_description.setText(item1.getDescription());
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");

            }
    private void renderImage() {
        if (item1.getImg() != null && !item1.getImg().isEmpty()) {
            if (item1.getImg().startsWith("http")) {
                j_img.setImage(new Image(item1.getImg()));
            } else {
                // Thử load từ resource trước
                String imgPath = "/controller/img/" + item1.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }
    @FXML
    private BorderPane Pane1;

    @FXML
    private Label j_CurrentPrice;

    @FXML
    private Label j_LabelName;

    @FXML
    private Button j_apply;

    @FXML
    private Label j_description;

    @FXML
    private ImageView j_image;

    @FXML
    private ImageView j_img;

    @FXML
    private Label j_name;

    @FXML
    private Label j_notified;

    @FXML
    private Button j_return;

    @FXML
    private Label j_status;

    @FXML
    private Label j_textSoDu;

    @FXML
    void On_MouseClickImg(MouseEvent event) {

    }

    @FXML
    void On_Return(ActionEvent event) {
        SceneHelper.changeScene((Node) j_return, "View3.fxml");

    }

    @FXML
    void On_apply(ActionEvent event) {

    }

}
