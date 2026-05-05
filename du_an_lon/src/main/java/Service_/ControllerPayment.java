package Service_;

import com.sun.glass.events.MouseEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

public class ControllerPayment {

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
            SceneHelper.changeScene(j_apply, "View3.fxml");

        }

        @FXML
        void On_apply(ActionEvent event) {

        }
        @FXML
        void On_MouseClickImg(javafx.scene.input.MouseEvent event) {
                // Viết code xử lý khi bấm vào ảnh ở đây
                System.out.println("Đã bấm vào ảnh Avatar!");
        }

    }

