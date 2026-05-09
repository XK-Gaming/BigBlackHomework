package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import model.User.User;
import model.User.UserSession;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;

public class ControllerAdmin{
    @FXML private Button LogOut;
    @FXML private Button j_ItemManager;
    @FXML private Button j_PaymentManager;
    @FXML private Button j_UserManager;
    @FXML private ImageView j_image;
    @FXML private Label lblRevenue;
    @FXML private LineChart<?, ?> lineChart;
    @FXML void On_ItemManager(ActionEvent event) {
        SceneHelper.changeScene(j_ItemManager, "ViewManagerItem.fxml");

    }
    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "View1.fxml");

    }

    @FXML
    void On_MouseClickImg(MouseEvent event) {
    }

    @FXML
    void On_PaymentManager(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "View3.1.fxml");
    }

    @FXML
    void On_UserManager(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "ViewManagerUser.fxml");
    }


    public void On_Filter(ActionEvent actionEvent) {
    }
}
