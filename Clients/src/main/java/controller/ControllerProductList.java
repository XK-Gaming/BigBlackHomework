package controller;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

// Danh sách sản phẩm seller.
public class ControllerProductList implements ServerListener {

    @FXML private TableView<Item> tableProducts;
    @FXML private TableColumn<Item, Integer> colId;
    @FXML private TableColumn<Item, String> colName;
    @FXML private TableColumn<Item, String> colCategory;
    @FXML private TableColumn<Item, Double> colPrice;

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

    private final Map<Integer, AuctionStatus> statusCache = new HashMap<>();

    private final Map<Integer, Double> currentPriceCache = new HashMap<>();
    private final ScheduledExecutorService statusRefreshScheduler = Executors.newSingleThreadScheduledExecutor(statusRefreshThreadFactory());
    private final List<ScheduledFuture<?>> statusRefreshTasks = new ArrayList<>();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    // Khởi tạo màn hình.
    @FXML
    public void initialize() {

        client.addListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            j_LabelName.setText(currentUser.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(currentUser.getBalance()) + " VNĐ");
        }

        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colPrice.setCellValueFactory(new PropertyValueFactory<>("startingPrice"));

        colCurrentPrice.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();

            Double currentPrice = currentPriceCache.getOrDefault(item.getDatabaseId(), item.getCurrentHighestPrice());
            return new SimpleObjectProperty<>(currentPrice);
        });

        colCategory.setCellValueFactory(cellData -> {
            String type = cellData.getValue().getItemType();
            return new SimpleStringProperty(type != null ? type : "");
        });

        colPrice.setCellFactory(column -> new TableCell<>() {
            // Cập nhật sản phẩm.
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

        colCurrentPrice.setCellFactory(column -> new TableCell<>() {
            // Cập nhật sản phẩm.
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                if (empty || price == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(String.format("%,.0f VNĐ", price));
                    setStyle("-fx-text-fill: #fd7e14; -fx-font-weight: bold;");
                }
            }
        });

        colTimeStart.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionStartTime()));
        colTimeEnd.setCellValueFactory(cellData -> formatInstant(cellData.getValue().getAuctionEndTime()));

        colSessionStatus.setCellValueFactory(cellData -> {
            Item item = cellData.getValue();
            AuctionStatus status = statusCache.get(item.getDatabaseId());
            return new SimpleObjectProperty<>(status);
        });

        colSessionStatus.setCellFactory(column -> new TableCell<>() {
            // Cập nhật sản phẩm.
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

        requestProductsFromNetwork();
    }
    // Yêu cầu sản phẩm.
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

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        if (response == null || response.command() == null) return;

        Command command = response.command();

        // Nhận sản phẩm seller.
        if (Command.GET_SELLER_ITEMS_RESULT.equals(command)) {
            Map<String, Object> data = (Map<String, Object>) response.payload();
            boolean isSuccess = (boolean) data.get("success");

            Platform.runLater(() -> {
                if (isSuccess) {
                    List<Item> serverItems = (List<Item>) data.get("items");
                    Map<Integer, String> stringStatusCache = (Map<Integer, String>) data.get("statusCache");

                    statusCache.clear();
                    currentPriceCache.clear();

                    if (stringStatusCache != null) {
                        stringStatusCache.forEach((id, statusStr) -> {
                            statusCache.put(id, AuctionStatus.valueOf(statusStr));
                        });
                    }

                    if (serverItems != null) {
                        for (Item item : serverItems) {
                            currentPriceCache.put(item.getDatabaseId(), item.getCurrentHighestPrice());
                        }
                    }

                    productList.setAll(serverItems != null ? serverItems : new ArrayList<>());
                    applyLocalTimeStatusUpdates();
                    scheduleStatusRefreshes();
                    tableProducts.setPlaceholder(new Label("Không có sản phẩm nào."));
                } else {
                    String errorMsg = (String) data.getOrDefault("message", "Lỗi không xác định từ Server.");
                    tableProducts.setPlaceholder(new Label("Không thể lấy dữ liệu: " + errorMsg));
                }
            });
        }

        // Nhận bid realtime.
        else if (Command.BID_UPDATE.equals(command) || Command.ITEMS_UPDATE.equals(command)) {
            handleRealtimeItemUpdate(response.payload());
        }

        // Nhận trạng thái phiên realtime.
        else if (Command.UPDATE_AUCTION_STATUS.equals(command)) {

            System.out.println("\n========================================================");
            System.out.println("[CLIENT LOG 1] Đã nhận tín hiệu UPDATE_AUCTION_STATUS từ mạng!");
            System.out.println("   + Chi tiết dữ liệu nhận về: " + response.payload());

            if (response.payload() instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();

                        final int targetItemId = itemIdStr.contains(".")
                                ? Double.valueOf(itemIdStr).intValue()
                                : Integer.parseInt(itemIdStr);

                        System.out.println("[CLIENT LOG 2] Phân tích ID thành công:");
                        System.out.println("   + Chuỗi thô: " + itemIdStr + " -> Ép kiểu số nguyên (int): " + targetItemId);

                        Platform.runLater(() -> {
                            System.out.println("[CLIENT LOG 3] Bắt đầu chạy trong luồng giao diện (Platform.runLater)...");

                            System.out.print("   + Các ID hiện có trên TableView: ");
                            productList.forEach(i -> System.out.print(i.getDatabaseId() + " "));
                            System.out.println();

                            Item targetItem = productList.stream()
                                    .filter(i -> i.getDatabaseId() == targetItemId)
                                    .findFirst()
                                    .orElse(null);

                            if (targetItem != null) {
                                System.out.println("[CLIENT LOG 4] Đã tìm thấy Sản phẩm trùng khớp trong bảng: " + targetItem.getName());
                            } else {
                                System.err.println("[CLIENT LOG 4 TẠCH] KHÔNG tìm thấy sản phẩm nào có ID " + targetItemId + " đang hiển thị trên bảng!");
                            }

                            if (map.containsKey("newStatus") && map.get("newStatus") != null) {
                                String newStatusStr = map.get("newStatus").toString();
                                AuctionStatus newStatus = AuctionStatus.valueOf(newStatusStr);

                                statusCache.put(targetItemId, newStatus);

                                System.out.println("[CLIENT LOG 5] Đã nạp trạng thái mới vào Cache hiển thị:");
                                System.out.println("   + statusCache.get(" + targetItemId + ") -> " + statusCache.get(targetItemId));
                            }

                            if (map.containsKey("currentPrice") && map.get("currentPrice") != null) {
                                double newPrice = Double.parseDouble(map.get("currentPrice").toString());
                                currentPriceCache.put(targetItemId, newPrice);
                                if (targetItem != null) {
                                    targetItem.setCurrentHighestPrice(newPrice);
                                }
                                System.out.println("[CLIENT LOG 6] Đã cập nhật giá mới vào bộ nhớ đệm: " + newPrice);
                            }

                            System.out.println("[CLIENT LOG 7] Tiến hành ép TableView vẽ lại dòng...");
                            if (targetItem != null) {
                                int index = productList.indexOf(targetItem);
                                if (index >= 0) {
                                    productList.set(index, targetItem);
                                    System.out.println("   => Đã kích hoạt sự kiện thay thế dòng tại vị trí index: " + index);
                                }
                            } else {
                                tableProducts.refresh();
                                System.out.println("   => Đã gọi lệnh làm mới toàn bảng (tableProducts.refresh())");
                            }
                            System.out.println("========================================================\n");
                        });
                    }
                } catch (Exception e) {
                    System.err.println("[CLIENT ERROR] Lỗi xử lý đổi màu giao diện: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        // Nhận kết quả xóa sản phẩm.
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
                        currentPriceCache.remove(deletedItemId);
                    }
                    String displayName = (itemName != null) ? " \"" + itemName + "\"" : "";
                    showAlert(Alert.AlertType.INFORMATION, "Thành công", "Sản phẩm" + displayName + " đã bị xóa!");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Không thể xóa", message);
                }
            });
        }

        // Nhận thông báo nạp tiền.
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
                        currentPriceCache.put(item.getDatabaseId(), item.getCurrentHighestPrice());
                        tableProducts.refresh();
                    });
                }
            }
        }

        // Nhận kết quả duyệt/dừng.
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

        // Nhận lệnh đăng xuất cưỡng chế.
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

        // Nhận kết quả đăng xuất.
        else if (Command.LOGOUT_RESULT.equals(command)) {
            Platform.runLater(() -> {
                stopRealtimeRefresh();
                AuctionClient.getInstance().closeConnection();
                UserSession.cleanUserSession();
                client.removeListener(this);
                SceneHelper.changeScene((Node) j_LabelName, "/fxml/LoginView.fxml");
            });
        }
    }
    // Nhận item realtime.
    private void handleRealtimeItemUpdate(Object payload) {
        RealtimeItemUpdate update = parseRealtimeItemUpdate(payload);
        if (update == null) {
            return;
        }

        Platform.runLater(() -> {
            Item targetItem = productList.stream()
                    .filter(i -> i.getDatabaseId() == update.itemId())
                    .findFirst()
                    .orElse(null);

            if (update.status() != null) {
                statusCache.put(update.itemId(), update.status());
            }

            if (update.price() != null) {
                currentPriceCache.put(update.itemId(), update.price());
                if (targetItem != null) {
                    targetItem.setCurrentHighestPrice(update.price());
                }
            }

            if (targetItem != null) {
                int index = productList.indexOf(targetItem);
                if (index >= 0) {
                    productList.set(index, targetItem);
                }
            } else {
                tableProducts.refresh();
            }
        });
    }
    // Đọc dữ liệu.
    private RealtimeItemUpdate parseRealtimeItemUpdate(Object payload) {
        if (payload instanceof Auction auction) {
            Item item = auction.getItem();
            if (item == null) {
                return null;
            }
            return new RealtimeItemUpdate(item.getDatabaseId(), auction.getRawStatus(), auction.getCurrentPrice());
        }

        if (!(payload instanceof Map<?, ?> map)) {
            return null;
        }

        Integer itemId = intValue(firstPresent(map, "itemId", "id"));
        AuctionStatus status = statusValue(firstPresent(map, "newStatus", "status"));
        Double price = doubleValue(firstPresent(map, "newPrice", "currentPrice"));

        Object itemObj = map.get("item");
        if (itemObj instanceof Item item) {
            if (itemId == null) {
                itemId = item.getDatabaseId();
            }
            if (price == null) {
                price = item.getCurrentHighestPrice();
            }
        }

        Object auctionObj = map.get("auction");
        if (auctionObj instanceof Auction auction) {
            Item item = auction.getItem();
            if (itemId == null && item != null) {
                itemId = item.getDatabaseId();
            }
            if (status == null) {
                status = auction.getRawStatus();
            }
            if (price == null) {
                price = auction.getCurrentPrice();
            }
        }

        return itemId == null ? null : new RealtimeItemUpdate(itemId, status, price);
    }

    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
    // Ép kiểu dữ liệu.
    private Integer intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Double.valueOf(value.toString()).intValue();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
    // Ép kiểu dữ liệu.
    private Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
    // Ép kiểu dữ liệu.
    private AuctionStatus statusValue(Object value) {
        if (value instanceof AuctionStatus status) {
            return status;
        }
        if (value != null) {
            try {
                return AuctionStatus.valueOf(value.toString());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
    // Lên lịch refresh trạng thái.
    private void scheduleStatusRefreshes() {
        cancelStatusRefreshes();
        Instant now = Instant.now();
        for (Item item : productList) {
            scheduleStatusRefresh(item.getAuctionStartTime(), now);
            scheduleStatusRefresh(item.getAuctionEndTime(), now);
        }
    }

    private void scheduleStatusRefresh(Instant boundary, Instant now) {
        if (boundary == null || boundary.isBefore(now)) {
            return;
        }
        long delayMs = Math.max(0, boundary.toEpochMilli() - now.toEpochMilli() + 100);
        ScheduledFuture<?> task = statusRefreshScheduler.schedule(() -> Platform.runLater(() -> {
            applyLocalTimeStatusUpdates();
            tableProducts.refresh();
            scheduleStatusRefreshes();
        }), delayMs, TimeUnit.MILLISECONDS);
        statusRefreshTasks.add(task);
    }
    // Cập nhật trạng thái local.
    private void applyLocalTimeStatusUpdates() {
        Instant now = Instant.now();
        for (Item item : productList) {
            AuctionStatus current = statusCache.get(item.getDatabaseId());
            if (current == AuctionStatus.PAID || current == AuctionStatus.CANCELLED) {
                continue;
            }
            AuctionStatus computed = computeStatusByTime(item, now);
            if (computed != null) {
                statusCache.put(item.getDatabaseId(), computed);
            }
        }
    }
    // Tính toán dữ liệu.
    private AuctionStatus computeStatusByTime(Item item, Instant now) {
        Instant start = item.getAuctionStartTime();
        Instant end = item.getAuctionEndTime();
        if (start == null || end == null) {
            return statusCache.getOrDefault(item.getDatabaseId(), AuctionStatus.OPEN);
        }
        if (!now.isBefore(end)) {
            return AuctionStatus.FINISHED;
        }
        if (!now.isBefore(start)) {
            return AuctionStatus.RUNNING;
        }
        return AuctionStatus.OPEN;
    }
    // Ẩn hoặc hủy trạng thái.
    private void cancelStatusRefreshes() {
        for (ScheduledFuture<?> task : statusRefreshTasks) {
            task.cancel(false);
        }
        statusRefreshTasks.clear();
    }
    // Dừng xử lý.
    private void stopRealtimeRefresh() {
        cancelStatusRefreshes();
        statusRefreshScheduler.shutdownNow();
    }

    private static ThreadFactory statusRefreshThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "product-list-status-refresh");
            thread.setDaemon(true);
            return thread;
        };
    }

    private record RealtimeItemUpdate(int itemId, AuctionStatus status, Double price) {
    }
    // Định dạng hiển thị.
    private SimpleStringProperty formatInstant(Instant instant) {
        if (instant == null) return new SimpleStringProperty("");
        return new SimpleStringProperty(formatter.format(instant));
    }

    // Xử lý nút giao diện.
    @FXML
    void On_AddProduct(ActionEvent event) {
        stopRealtimeRefresh();
        client.removeListener(this);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/SellerView.fxml");
    }

    // Xử lý nút giao diện.
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

        stopRealtimeRefresh();
        client.removeListener(this);
        ControllerEditProduct editController = SceneHelper.changeSceneAndGetController(
                (Node) event.getSource(), "/fxml/EditProductView.fxml"
        );

        if (editController != null) {
            editController.initData(selectedItem, status, hasBids);
        }
    }

    // Xử lý nút giao diện.
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

    // Đăng xuất.
    @FXML
    void On_LogOut (ActionEvent event) {
        stopRealtimeRefresh();
        try {
            client.sendCommand(Command.LOGOUT, UserSession.getLoggedInUser().getUsername());
        } catch (IOException e) {
            System.err.println("Lỗi kết nối khi gửi yêu cầu Đăng xuất: " + e.getMessage());
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
}
