package controller; // Thêm gói package tương ứng nếu cần

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import model.Items.Item;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.AuctionStatus;
import network.*;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ControllerProductList implements ServerListener {

    @FXML private TableView<Item> tableProducts;
    @FXML private TableColumn<Item, Integer> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colCategory;
    @FXML private TableColumn<Item, Double> colPrice; // Đây là giá khởi điểm (Starting Price)

    // 🚀 BỔ SUNG: Cột hiển thị Giá hiện tại
    @FXML private TableColumn<Item, Double> colCurrentPrice;

    @FXML private TableColumn<Item, String> colTimeStart;
    @FXML private TableColumn<Item, String> colTimeEnd;
    @FXML private TableColumn<Item, AuctionStatus> colSessionStatus;

    @FXML private TextField txtSearch;
    @FXML private Label j_LabelName;
    @FXML private Label j_textSoDu;

    @FXML private Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    private final AuctionClient client = AuctionClient.getInstance();
    private final ObservableList<Item> productList = FXCollections.observableArrayList();
    private FilteredList<Item> filteredData;

    // Bộ nhớ đệm quản lý trạng thái đồng bộ dữ liệu nhận từ mạng
    private final Map<Integer, AuctionStatus> statusCache = new HashMap<>();

    // 🚀 BỔ SUNG: Bộ nhớ đệm quản lý giá hiện tại (Real-time Price Cache)
    private final Map<Integer, Double> currentPriceCache = new HashMap<>();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        // Thiết lập lắng nghe phản hồi từ máy chủ hệ thống
        client.setListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        // 1. Thông tin user cá nhân
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            j_LabelName.setText(currentUser.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(currentUser.getBalance()) + " VNĐ");
        }

        // 2. Cấu hình ánh xạ cột hiển thị danh sách
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        // 🚀 BỔ SUNG: Ánh xạ dữ liệu động cho cột Giá hiện tại từ Cache
        colCurrentPrice.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            // Nếu trong cache chưa có giá hiện tại, mặc định lấy giá cao nhất hiện có từ item hoặc giá khởi điểm
            Double currentPrice = currentPriceCache.getOrDefault(item.getDatabaseId(), item.getCurrentHighestPrice());
            return new SimpleObjectProperty<>(currentPrice);
        });

        colCategory.setCellValueFactory(cellData -> {
            String type = cellData.getValue().getItemType();
            return new SimpleStringProperty(type != null ? type : "");
        });

        // Định dạng tiền tệ cho cột Giá khởi điểm
        colPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                } else {
                    setText(String.format("%,.0f VNĐ", price));
                }
            }
        });

        // 🚀 BỔ SUNG: Định dạng tiền tệ và kiểu dáng (Bold màu cam) cho cột Giá hiện tại
        colCurrentPrice.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.0f VNĐ", price));
                    setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;"); // Màu cam nổi bật cho giá hiện tại
                }
            }
        });

        colTimeStart.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionStartTime()));
        colTimeEnd.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionEndTime()));

        // Trạng thái được ánh xạ động từ vùng Cache nhận từ mạng
        colSessionStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            AuctionStatus status = statusCache.get(item.getDatabaseId());
            return new SimpleObjectProperty<>(status);
        });

        colSessionStatus.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(AuctionStatus status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    switch (status) {
                        case OPEN:
                            setText("Sắp diễn ra");
                            setStyle("-fx-text-fill: #17a2b8; -fx-font-weight: bold;");
                            break;
                        case RUNNING:
                            setText("Đang diễn ra");
                            setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");
                            break;
                        case FINISHED:
                            setText("Đã kết thúc");
                            setStyle("-fx-text-fill: #dc3545; -fx-font-weight: bold;");
                            break;
                        case PAID:
                            setText("Đã thanh toán");
                            setStyle("-fx-text-fill: #007bff; -fx-font-weight: bold;");
                            break;
                        case CANCELLED:
                            setText("Đã hủy");
                            setStyle("-fx-text-fill: #6c757d; -fx-font-style: italic;");
                            break;
                        default:
                            setText(status.toString());
                            setStyle("");
                            break;
                    }
                }
            }
        });

        // 3. Bộ lọc tìm kiếm thời gian thực (Real-time Filter)
        filteredData = new FilteredList<>(productList, p -> true);
        txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredData.setPredicate(item -> {
                if (newValue == null || newValue.trim().isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase().trim();
                if (item.getName() != null && item.getName().toLowerCase().contains(lowerCaseFilter)) {
                    return true;
                }
                return String.valueOf(item.getDatabaseId()).contains(lowerCaseFilter);
            });
        });
        tableProducts.setItems(filteredData);

        // 4. Phát lệnh mạng yêu cầu lấy danh sách sản phẩm từ Server
        requestProductsFromNetwork();
    }

    private void requestProductsFromNetwork() {
        User currentUser = UserSession.getLoggedInUser();
        if (currentUser == null) return;

        tableProducts.setPlaceholder(new ProgressIndicator());

        ClientNetworkExecutor.execute(() -> {
            try {
                client.sendCommand(Command.GET_SELLER_ITEMS, currentUser.getUsername());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void onServerResponse(DataPacket response) {
        if (response == null || response.command() == null) return;

        Command command = response.command();

        // --- CASE 1: Nhận danh sách sản phẩm ---
        if (Command.GET_SELLER_ITEMS_RESULT.equals(command)) {
            Map<String, Object> data = (Map<String, Object>) response.payload();
            boolean isSuccess = (boolean) data.get("success");

            Platform.runLater(() -> {
                if (isSuccess) {
                    List<Item> serverItems = (List<Item>) data.get("items");
                    Map<Integer, String> stringStatusCache = (Map<Integer, String>) data.get("statusCache");

                    // Làm sạch và đồng bộ dữ liệu trạng thái mới
                    statusCache.clear();
                    currentPriceCache.clear(); // Đột phá làm sạch cache giá cũ

                    if (stringStatusCache != null) {
                        stringStatusCache.forEach((id, statusStr) -> {
                            statusCache.put(id, AuctionStatus.valueOf(statusStr));
                        });
                    }

                    // Khởi tạo giá trị ban đầu cho `currentPriceCache` từ danh sách Items nhận về
                    if (serverItems != null) {
                        for (Item item : serverItems) {
                            currentPriceCache.put(item.getDatabaseId(), item.getCurrentHighestPrice());
                        }
                    }

                    productList.setAll(serverItems != null ? serverItems : new ArrayList<>());
                    tableProducts.setPlaceholder(new Label("Không có sản phẩm nào."));
                } else {
                    String errorMsg = (String) data.getOrDefault("message", "Lỗi không xác định từ Server.");
                    tableProducts.setPlaceholder(new Label("Không thể lấy dữ liệu: " + errorMsg));
                }
            });
        }

        // 🚀 --- CASE 2: Đón nhận cập nhật trạng thái tự động và Cập nhật GIÁ MỚI nếu có ---
        else if (Command.UPDATE_AUCTION_STATUS.equals(command)) {
            if (response.payload() instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();
                        int targetItemId = itemIdStr.contains(".") ? Double.valueOf(itemIdStr).intValue() : Integer.parseInt(itemIdStr);

                        Platform.runLater(() -> {
                            // 1. Cập nhật trạng thái
                            if (map.containsKey("newStatus") && map.get("newStatus") != null) {
                                String newStatusStr = map.get("newStatus").toString();
                                statusCache.put(targetItemId, AuctionStatus.valueOf(newStatusStr));
                                System.out.println("[Product List] Sản phẩm " + targetItemId + " đổi trạng thái: " + newStatusStr);
                            }

                            // 🚀 2. Cập nhật Giá hiện tại theo thời gian thực (Nếu Server đẩy kèm giá mới trong gói Update)
                            if (map.containsKey("currentPrice") && map.get("currentPrice") != null) {
                                double newPrice = Double.parseDouble(map.get("currentPrice").toString());
                                currentPriceCache.put(targetItemId, newPrice);
                                System.out.println("[Product List] Sản phẩm " + targetItemId + " cập nhật giá mới: " + newPrice);
                            }

                            // Ép TableView vẽ lại để cập nhật cả Trạng thái lẫn Giá hiện tại lên UI ngay lập tức
                            tableProducts.refresh();
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Lỗi xử lý đồng bộ trạng thái & giá Real-time: " + e.getMessage());
                }
            }
        }

        // --- CASE 3: Nhận kết quả phản hồi xóa sản phẩm ---
        else if (Command.DELETE_ITEM_RESULT.equals(command)) {
            Map<String, Object> resData = (Map<String, Object>) response.payload();
            boolean success = (boolean) resData.get("success");
            String message = (String) resData.get("message");
            String itemName = (String) resData.get("itemName");

            Platform.runLater(() -> {
                if (success) {
                    int deletedItemId = ((Number) resData.get("deletedItemId")).intValue();

                    Item itemToRemove = productList.stream()
                            .filter(item -> item.getDatabaseId() == deletedItemId)
                            .findFirst()
                            .orElse(null);

                    if (itemToRemove != null) {
                        productList.remove(itemToRemove);
                        statusCache.remove(deletedItemId);
                        currentPriceCache.remove(deletedItemId); // Xóa khỏi cache giá
                    }
                    String displayName = (itemName != null) ? " \"" + itemName + "\"" : "";
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Sản phẩm" + displayName + " đã bị xóa!");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Không thể xóa", message);
                }
            });
        }

        // --- CASE 4: Thông báo thanh toán hóa đơn thành công (Nhận tiền về số dư) ---
        else if (Command.NOTIFICATION_NEW_PAY.equals(command)) {
            User user = UserSession.getLoggedInUser();
            if (user != null && response.payload() instanceof Map) {
                Map<String, Object> notifData = (Map<String, Object>) response.payload();
                Object itemObj = notifData.get("item");

                if (itemObj instanceof Item item) {
                    user.setBalance(user.getBalance() + item.getCurrentHighestPrice());
                    Platform.runLater(() -> {
                        ControllerNotificationSeller.handleSuccessToastNotificationSeller(response.payload(), j_textSoDu, UserSession.getLoggedInUser());
                        statusCache.put(item.getDatabaseId(), AuctionStatus.PAID);
                        currentPriceCache.put(item.getDatabaseId(), item.getCurrentHighestPrice()); // Đồng bộ giá cuối
                        tableProducts.refresh();
                    });
                }
            }
        }

        // --- CASE 5: Phản hồi phê duyệt từ Admin ---
        else if (Command.SET_ALLOW_RESULT.equals(command)) {
            if (response.payload() instanceof Map) {
                Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
                boolean isAllow = responsePayload.get("allow") != null && responsePayload.get("allow").toString().equals("true");
                String itemName = "";
                Object auctionObj = responsePayload.get("auction");
                if (auctionObj instanceof Auction) {
                    Auction auction = (Auction) auctionObj;
                    if (auction.getItem() != null) {
                        itemName = " \"" + auction.getItem().getName() + "\"";
                    }
                }

                String finalItemName = itemName;
                Platform.runLater(() -> {
                    showAlert(Alert.AlertType.INFORMATION, "Thông báo", isAllow ? "Phiên đấu giá" + finalItemName + " đã được phê duyệt!" : "Phiên đấu giá" + finalItemName + " đã bị tạm dừng!");
                    requestProductsFromNetwork();
                });
            }
        }

        // --- CASE 6: Tài khoản bị xóa cưỡng chế ---
        else if (Command.FORCE_LOGOUT.equals(command)) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Tài khoản bị xóa");
                alert.setHeaderText(null);
                alert.setContentText("Tài khoản của bạn đã bị Admin xóa. Ứng dụng sẽ tự đóng.");
                alert.showAndWait();
                System.exit(0);
            });
        }

        // --- CASE 7: Kết quả đăng xuất thủ công thành công ---
        else if (Command.LOGOUT_RESULT.equals(command)) {
            Platform.runLater(() -> {
                AuctionClient.getInstance().closeConnection();
                UserSession.cleanUserSession();
                SceneHelper.changeScene((Node) j_LabelName, "/fxml/LoginView.fxml");
            });
        }
    }

    private SimpleStringProperty formatInstant(Instant instant) {
        if (instant == null) return new SimpleStringProperty("");
        return new SimpleStringProperty(formatter.format(instant));
    }

    @FXML
    void On_AddProduct(ActionEvent event) {
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/SellerView.fxml");
    }

    @FXML
    void On_EditProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm trong danh sách để sửa!");
            return;
        }

        AuctionStatus status = statusCache.get(selectedItem.getDatabaseId());
        if (status == null) status = AuctionStatus.OPEN;

        if (status == AuctionStatus.FINISHED || status == AuctionStatus.PAID) {
            showAlert(Alert.AlertType.ERROR, "Bị từ chối", "Phiên đấu giá đã kết thúc/thanh toán. Không thể sửa!");
            return;
        }

        boolean hasBids = false;

        ControllerEditProduct editController = SceneHelper.changeSceneAndGetController(
                (Node) event.getSource(), "/fxml/EditProductView.fxml"
        );

        if (editController != null) {
            editController.initData(selectedItem, status, hasBids);
        }
    }

    @FXML
    void On_DeleteProduct(ActionEvent event) {
        Item selectedItem = tableProducts.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            showAlert(Alert.AlertType.WARNING, "Thông báo", "Vui lòng chọn một sản phẩm để xóa!");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận xóa");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc chắn muốn xóa sản phẩm '" + selectedItem.getName() + "' không?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {

            ClientNetworkExecutor.execute(() -> {
                try {
                    client.sendCommand(Command.DELETE_ITEM, selectedItem);
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        showAlert(Alert.AlertType.ERROR, "Lỗi kết nối", "Không thể gửi yêu cầu xóa đến máy chủ!");
                    });
                }
            });
        }
    }

    @FXML
    void On_LogOut (ActionEvent event) {
        try {
            client.sendCommand(Command.LOGOUT, UserSession.getLoggedInUser().getUsername());
        } catch (IOException e) {
            System.err.println("Lỗi kết nối khi gửi yêu cầu Đăng xuất: " + e.getMessage());
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