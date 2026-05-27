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
import javafx.scene.shape.Circle;
import model.DepositTransaction;
import model.Items.Item;
import model.User.User;
import model.auction.BidHistoryDTO;
import network.*;
import java.io.IOException;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    @FXML private TableColumn<User, String> colStatus;

    @FXML private Label detailUsername;
    @FXML private Label detailRole;
    @FXML private Label paymentHistory;
    @FXML private ListView<String> listBidHistory;
    @FXML private ListView<String> listSellHistory;
    @FXML private ListView<String> listTransactionHistory;

    private final AuctionClient client = AuctionClient.getInstance();
    private ObservableList<User> masterData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        client.setListener(this);
        setupTable();
        setupComboBox();
        loadData();

        tableUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            if (newSelection != null) {
                showUserDetails(newSelection);
            }
        });
    }

    private void setupTable() {
        colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        colPassword.setCellValueFactory(new PropertyValueFactory<>("password"));
        colRole.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRole_toString()));
        
        // Cột trạng thái Online/Offline
        TableColumn<User, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        tableUsers.getColumns().add(statusCol);
    }

    private void setupComboBox() {
        cbRole.setItems(FXCollections.observableArrayList("-- Tất cả vai trò --", "Admin", "Người bán", "Người đấu giá"));
        cbRole.getSelectionModel().selectFirst();
    }

    private void loadData() {
        try {
            client.sendCommand(network.Command.GET_ALL_USERS, (Object) "");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showUserDetails(User user) {
        detailUsername.setText(user.getUsername());
        detailRole.setText("Vai trò: " + user.getRole_toString());
        paymentHistory.setText("Số dư: " + String.format("%,.0f VNĐ", user.getBalance()));

        // Xóa dữ liệu cũ
        listBidHistory.getItems().clear();
        listSellHistory.getItems().clear();
        listTransactionHistory.getItems().clear();

        // Gửi yêu cầu lấy chi tiết từ server
        try {
            client.sendCommand(network.Command.GET_BIDDER_HISTORY, (Object) user.getUsername());
            client.sendCommand(network.Command.GET_SELLER_ITEMS, (Object) user.getUsername());
            
            // Hiển thị lịch sử giao dịch nạp tiền nếu có
            if (user.getDepositHistory() != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                for (DepositTransaction dt : user.getDepositHistory()) {
                    String info = String.format("[%s] %s: %,.0f VNĐ",
                            dt.getTimestamp().format(formatter),
                            dt.getStatus(),
                            dt.getAmount());
                    listTransactionHistory.getItems().add(info);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    @FXML
    public void On_UserManager(ActionEvent event) {
        loadData();
    }

    @FXML
    public void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene(LogOut, "/fxml/AdminView.fxml");
    }

    @FXML
    public void On_MouseClickImg(MouseEvent mouseEvent) {
    }

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

    @Override
    public void onServerResponse(network.DataPacket response) {
        network.Command command = response.command();
        Object payload = response.payload();

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
                listBidHistory.getItems().clear();
                for (BidHistoryDTO dto : history) {
                    if ("WON".equals(dto.getStatus())) {
                        listBidHistory.getItems().add(dto.getItemName() + " - Thắng: " + String.format("%,.0f", dto.getMyHighestBid()) + " VNĐ");
                    }
                }
            });
        } else if (network.Command.GET_SELLER_ITEMS_RESULT.equals(command)) {
            Map<String, Object> res = (Map<String, Object>) payload;
            List<Item> items = (List<Item>) res.get("items");
            Map<Integer, String> statusCache = (Map<Integer, String>) res.get("statusCache");
            Platform.runLater(() -> {
                listSellHistory.getItems().clear();
                if (items != null) {
                    for (Item item : items) {
                        String status = statusCache.getOrDefault(item.getDatabaseId(), "OPEN");
                        listSellHistory.getItems().add(item.getName() + " [" + status + "] - " + String.format("%,.0f", item.getStartingPrice()) + " VNĐ");
                    }
                }
            });
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
