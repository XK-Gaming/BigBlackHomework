package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.shape.Circle;
import model.Items.Item;
import model.User.User;
import model.User.UserRole;
import network.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Màn dashboard admin.
public class ControllerAdmin implements ServerListener {

    @FXML private BorderPane Pane1;
    @FXML private Button LogOut;
    @FXML private Button j_ItemManager;
    @FXML private Button j_PaymentManager;
    @FXML private Button j_UserManager;
    @FXML private ImageView j_image;
    @FXML private Label j_LabelName;

    @FXML private Label lblTotalItems;
    @FXML private Label lblTotalUsers;
    @FXML private PieChart categoryPieChart;

    @FXML private Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    private AuctionClient client = AuctionClient.getInstance();

    // Khởi tạo màn hình.
    @FXML
    public void initialize() {

        client.addListener(this);

        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        try {
            client.sendCommand(Command.SELECT_ITEMS, UserRole.ADMIN);
            client.sendCommand(Command.GET_ALL_USERS, null);
        } catch (IOException e) {
            System.err.println("Lỗi gửi lệnh khởi tạo dữ liệu Dashboard: " + e.getMessage());
        }
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();
        Object payload = response.payload();

        // Nhận danh sách sản phẩm.
        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            List<Item> itemsFromServer = (List<Item>) payload;

            Platform.runLater(() -> {

                lblTotalItems.setText(String.format("%,d", itemsFromServer.size()));

                Map<String, Integer> typeCounterMap = new HashMap<>();

                for (Item item : itemsFromServer) {
                    String itemType = item.getItemType();

                    if (itemType != null && !itemType.trim().isEmpty()) {

                        typeCounterMap.put(itemType, typeCounterMap.getOrDefault(itemType, 0) + 1);
                    }
                }

                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                for (Map.Entry<String, Integer> entry : typeCounterMap.entrySet()) {
                    pieChartData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
                }

                categoryPieChart.getData().clear();
                categoryPieChart.setData(pieChartData);
            });
        }

        // Nhận danh sách user.
        if (Command.GET_ALL_USERS_RESULT.equals(command)) {
            List<User> users = (List<User>) payload;

            Platform.runLater(() -> {

                lblTotalUsers.setText(String.format("%,d", users.size()));
            });
        }
    }

    // Xử lý nút giao diện.
    @FXML
    void On_ItemManager(ActionEvent event) {

        client.removeListener(this);
        SceneHelper.changeScene(j_ItemManager, "/fxml/AdminItemManagerView.fxml");
    }

    // Xử lý nút giao diện.
    @FXML
    void On_UserManager(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(j_UserManager, "/fxml/AdminUserManagerView.fxml");
    }

    // Xử lý nút giao diện.
    @FXML
    void On_PaymentManager(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(j_PaymentManager, "/fxml/ViewAdminViewerPayment.fxml");
    }

    // Đăng xuất.
    @FXML
    void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(LogOut, "/fxml/LoginView.fxml");
    }
    // Xử lý nút giao diện.
    // Lọc dữ liệu.
    @FXML void On_MouseClickImg(MouseEvent event) {}
    @FXML void On_Filter(ActionEvent actionEvent) {}
}
