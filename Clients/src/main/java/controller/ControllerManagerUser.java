package controller;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import model.DepositTransaction;
import model.Items.Item;
import model.User.User;
import model.auction.BidHistoryDTO;
import network.*;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Quản lý user admin.
public class ControllerManagerUser implements ServerListener {

    @FXML private BorderPane Pane1;
    @FXML private ImageView j_image;
    @FXML private Label j_LabelName;
    @FXML private Button j_UserManager;
    @FXML private Button LogOut;
    @FXML private Label connectionText;
    @FXML private Circle connectionStatus;
    @FXML private ComboBox<String> cbRole;

    @FXML private TableView<User> tableUsers;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colPassword;
    @FXML private TableColumn<User, String> colRole;

    @FXML private Label detailUsername;
    @FXML private Label detailRole;
    @FXML private Label paymentHistory;

    @FXML private TableView<String> tableTransactionHistory;
    @FXML private TableView<String> tableBidHistory;
    @FXML private TableView<String> tableSellHistory;

    @FXML private Button btnRefresh;

    private final AuctionClient client = AuctionClient.getInstance();
    private ObservableList<User> masterData = FXCollections.observableArrayList();
    private ConnectionStatusManager statusManager;

    // Khởi tạo màn hình.
    @FXML
    public void initialize() {
        client.addListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();
        setupTable();
        setupComboBox();
        loadData();

        tableUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showUserDetails(newSelection);
            }
        });
    }
    // Cấu hình bảng.
    private void setupTable() {

        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colPassword.setCellValueFactory(new PropertyValueFactory<>("password"));
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRole_toString()));

        TableColumn<String, String> colTx = new TableColumn<>("Chi tiết lịch sử");
        colTx.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        tableTransactionHistory.getColumns().add(colTx);

        TableColumn<String, String> colBid = new TableColumn<>("Sản phẩm trúng giải");
        colBid.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        tableBidHistory.getColumns().add(colBid);

        TableColumn<String, String> colSell = new TableColumn<>("Thông tin đăng bán");
        colSell.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        tableSellHistory.getColumns().add(colSell);

        TableColumn<User, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setCellFactory(column -> new TableCell<User, String>() {
            private final Circle dot = new Circle(4);
            private final Label label = new Label();
            private final HBox container = new HBox(6, dot, label);
            { container.setAlignment(javafx.geometry.Pos.CENTER_LEFT); }

            // Cập nhật sản phẩm.
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    label.setText(item.toUpperCase());
                    String statusClean = item.trim().toUpperCase();
                    if (statusClean.contains("ONLINE") || statusClean.contains("ON")) {
                        label.setTextFill(Color.web("#16a34a"));
                        dot.setFill(Color.web("#16a34a"));
                    } else {
                        label.setTextFill(Color.web("#dc2626"));
                        dot.setFill(Color.web("#dc2626"));
                    }
                    setGraphic(container);
                }
            }
        });
        tableUsers.getColumns().add(statusCol);
    }
    // Cấu hình ban đầu.
    private void setupComboBox() {
        cbRole.setItems(FXCollections.observableArrayList("-- Tất cả vai trò --", "Admin", "Người bán", "Người đấu giá"));
        cbRole.getSelectionModel().selectFirst();
    }
    // Tải dữ liệu.
    private void loadData() {
        try {
            client.sendCommand(network.Command.GET_ALL_USERS, (Object) "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Hiện chi tiết user.
    private void showUserDetails(User user) {
        detailUsername.setText(user.getUsername());
        detailRole.setText("Vai trò: " + user.getRole_toString());
        paymentHistory.setText("Số dư: " + String.format("%,.0f VNĐ", user.getBalance()));

        tableBidHistory.getItems().clear();
        tableSellHistory.getItems().clear();
        tableTransactionHistory.getItems().clear();

        try {
            client.sendCommand(network.Command.GET_BIDDER_HISTORY, (Object) user.getUsername());
            client.sendCommand(network.Command.GET_SELLER_ITEMS, (Object) user.getUsername());

            if (user.getDepositHistory() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZoneId.systemDefault());

                for (DepositTransaction dt : user.getDepositHistory()) {
                    if (dt.getTimestamp() != null) {
                        String formattedTime = formatter.format(dt.getTimestamp());
                        String info = String.format("[%s] %s: %,.0f VNĐ",
                                formattedTime,
                                dt.getStatus(),
                                dt.getAmount());
                        tableTransactionHistory.getItems().add(info);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Lọc dữ liệu.
    @FXML
    public void On_Filter(ActionEvent event) {
        String selectedRole = cbRole.getSelectionModel().getSelectedItem();
        if (selectedRole == null || selectedRole.equals("-- Tất cả vai trò --")) {
            tableUsers.setItems(masterData);
        } else {
            List<User> filtered = masterData.stream()
                    .filter(u -> u.getRole_toString().equals(selectedRole))
                    .collect(Collectors.toList());
            tableUsers.setItems(FXCollections.observableArrayList(filtered));
        }
    }

    // Xử lý nút giao diện.
    @FXML
    public void On_UserManager(ActionEvent event) {
        loadData();
    }

    // Đăng xuất.
    @FXML
    public void On_LogOut(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(LogOut, "/fxml/AdminView.fxml");
    }
    // Xử lý nút giao diện.
    @FXML public void On_MouseClickImg(MouseEvent mouseEvent) {}

    // Xóa user.
    @FXML
    public void On_DeleteUser(ActionEvent event) {
        User selected = tableUsers.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn người dùng cần xóa!");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Bạn có chắc chắn muốn xóa người dùng " + selected.getUsername() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    client.sendCommand(network.Command.DELETE_USER, (Object) selected.getUsername());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(network.DataPacket response) {
        network.Command command = response.command();
        Object payload = response.payload();

        // Nhận danh sách user.
        if (network.Command.GET_ALL_USERS_RESULT.equals(command)) {
            List<User> users = (List<User>) payload;
            Platform.runLater(() -> {
                masterData.setAll(users);
                tableUsers.setItems(masterData);
            });
        } else if (network.Command.DELETE_USER_RESULT.equals(command)) {
            Map<String, Object> res = (Map<String, Object>) payload;
            boolean success = (boolean) res.get("success");
            String message = (String) res.get("message");
            Platform.runLater(() -> {
                showAlert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Kết quả", message);
                if (success) {
                    String deletedUser = (String) res.get("username");
                    masterData.removeIf(u -> u.getUsername().equals(deletedUser));
                }
            });
        } else if (network.Command.FORCE_LOGOUT.equals(command)) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Thông báo hệ thống");
                alert.setHeaderText(null);
                alert.setContentText((String) payload);
                alert.showAndWait();
                System.exit(0);
            });
        } else if (network.Command.GET_BIDDER_HISTORY_RESULT.equals(command)) {
            List<BidHistoryDTO> history = (List<BidHistoryDTO>) payload;
            Platform.runLater(() -> {
                tableBidHistory.getItems().clear();
                for (BidHistoryDTO dto : history) {
                    if ("WON".equals(dto.getStatus())) {
                        tableBidHistory.getItems().add(dto.getItemName() + " - Thắng: " + String.format("%,.0f", dto.getMyHighestBid()) + " VNĐ");
                    }
                }
            });
        } else if (network.Command.GET_SELLER_ITEMS_RESULT.equals(command)) {
            Map<String, Object> res = (Map<String, Object>) payload;
            List<Item> items = (List<Item>) res.get("items");
            Map<Integer, String> statusCache = (Map<Integer, String>) res.get("statusCache");
            Platform.runLater(() -> {
                tableSellHistory.getItems().clear();
                if (items != null) {
                    for (Item item : items) {
                        String status = statusCache.getOrDefault(item.getDatabaseId(), "OPEN");
                        tableSellHistory.getItems().add(item.getName() + " [" + status + "] - " + String.format("%,.0f", item.getStartingPrice()) + " VNĐ");
                    }
                }
            });
        }
    }
    // Hiển thị giao diện.
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    // Làm mới dữ liệu.
    @FXML
    public void On_RefreshData(ActionEvent event) {
        loadData();
        tableUsers.getSelectionModel().clearSelection();
        tableTransactionHistory.getItems().clear();
        tableBidHistory.getItems().clear();
        tableSellHistory.getItems().clear();
    }
}
