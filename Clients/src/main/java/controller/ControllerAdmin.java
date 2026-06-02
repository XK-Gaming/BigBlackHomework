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

public class ControllerAdmin implements ServerListener {

    @FXML private BorderPane Pane1;
    @FXML private Button LogOut;
    @FXML private Button j_ItemManager;
    @FXML private Button j_PaymentManager;
    @FXML private Button j_UserManager;
    @FXML private ImageView j_image;
    @FXML private Label j_LabelName;

    // Các thành phần UI hiển thị dữ liệu động thống kê
    @FXML private Label lblTotalItems;
    @FXML private Label lblTotalUsers;
    @FXML private PieChart categoryPieChart;

    // Các thành phần trạng thái kết nối mạng
    @FXML private Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    private AuctionClient client = AuctionClient.getInstance();

    @FXML
    public void initialize() {
        // 1. Đăng ký nhận phản hồi từ hệ thống Server mạng Socket
        client.addListener(this);

        // 2. Kích hoạt tiến trình theo dõi trạng thái mạng (Báo xanh/đỏ)
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        // 3. Gửi lệnh yêu cầu Server đổ toàn bộ dữ liệu Sản phẩm & Người dùng về để thống kê
        try {
            client.sendCommand(Command.SELECT_ITEMS, UserRole.ADMIN);
            client.sendCommand(Command.GET_ALL_USERS, null);
        } catch (IOException e) {
            System.err.println("Lỗi gửi lệnh khởi tạo dữ liệu Dashboard: " + e.getMessage());
        }
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();
        Object payload = response.payload();

        // TH1: Nhận danh sách sản phẩm -> Đếm số lượng & Gom nhóm vẽ biểu đồ hình quạt
        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            List<Item> itemsFromServer = (List<Item>) payload;

            Platform.runLater(() -> {
                // Hiển thị tổng số lượng sản phẩm lên thẻ đầu tiên
                lblTotalItems.setText(String.format("%,d", itemsFromServer.size()));

                // Sử dụng Map để làm bộ đếm tần suất xuất hiện của từng loại thuộc tính item.getType()
                Map<String, Integer> typeCounterMap = new HashMap<>();

                for (Item item : itemsFromServer) {
                    String itemType = item.getItemType(); // Lấy thuộc tính getItemType từ Model Item của bạn

                    if (itemType != null && !itemType.trim().isEmpty()) {
                        // Cộng dồn: Nếu chưa tồn tại loại này thì mặc định là 0 + 1, nếu có rồi thì lấy số cũ + 1
                        typeCounterMap.put(itemType, typeCounterMap.getOrDefault(itemType, 0) + 1);
                    }
                }

                // Chuyển dữ liệu từ Map sang ObservableList để nạp vào PieChart công cộng
                ObservableList<PieChart.Data> pieChartData = FXCollections.observableArrayList();
                for (Map.Entry<String, Integer> entry : typeCounterMap.entrySet()) {
                    pieChartData.add(new PieChart.Data(entry.getKey(), entry.getValue()));
                }

                // Dọn sạch hình quạt cũ và vẽ phân tách biểu đồ quạt mới diện rộng
                categoryPieChart.getData().clear();
                categoryPieChart.setData(pieChartData);
            });
        }

        // TH2: Nhận danh sách người dùng -> Đếm tổng số lượng người dùng hệ thống
        if (Command.GET_ALL_USERS_RESULT.equals(command)) {
            List<User> users = (List<User>) payload;

            Platform.runLater(() -> {
                // Hiển thị tổng số lượng người dùng lên thẻ thứ hai
                lblTotalUsers.setText(String.format("%,d", users.size()));
            });
        }
    }

    @FXML
    void On_ItemManager(ActionEvent event) {
        // Gỡ Listener của màn hình hiện tại trước khi đổi Scene để tránh rò rỉ bộ nhớ
        client.removeListener(this);
        SceneHelper.changeScene(j_ItemManager, "/fxml/AdminItemManagerView.fxml");
    }

    @FXML
    void On_UserManager(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(j_UserManager, "/fxml/AdminUserManagerView.fxml");
    }

    @FXML
    void On_PaymentManager(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(j_PaymentManager, "/fxml/ViewAdminViewerPayment.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(LogOut, "/fxml/LoginView.fxml");
    }

    @FXML void On_MouseClickImg(MouseEvent event) {}
    @FXML void On_Filter(ActionEvent actionEvent) {}
}