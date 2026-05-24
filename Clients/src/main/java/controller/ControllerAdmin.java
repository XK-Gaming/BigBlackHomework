package controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;

public class ControllerAdmin{
    @FXML private Button LogOut;
    @FXML private Button j_ItemManager;
    @FXML private Button j_PaymentManager;
    @FXML private Button j_UserManager;
    @FXML private ImageView j_image;
    @FXML private Label lblRevenue;
    @FXML private LineChart<?, ?> lineChart;

    @FXML private Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    public void initialize() {
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();
    }
    @FXML void On_ItemManager(ActionEvent event) {
        SceneHelper.changeScene(j_ItemManager, "/fxml/AdminItemManagerView.fxml");}
    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "/fxml/LoginView.fxml");
    }

    @FXML
    void On_MouseClickImg(MouseEvent event) {
    }

    @FXML
    void On_PaymentManager(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "/fxml/ViewAdminViewerPayment.fxml");
    }

    @FXML
    void On_UserManager(ActionEvent event) {
        SceneHelper.changeScene(j_PaymentManager, "/fxml/AdminUserManagerView.fxml");
    }


    public void On_Filter(ActionEvent actionEvent) {
    }
}
