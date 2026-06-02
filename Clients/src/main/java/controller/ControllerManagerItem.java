package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import model.Items.Item;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;
import network.*;
import javafx.scene.shape.Circle;
import javafx.scene.control.Alert;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerManagerItem implements ServerListener {
    private FilteredList<Item> filteredAssets;
    private User p1 = UserSession.getLoggedInUser();
    private AuctionClient client = AuctionClient.getInstance();

    private Auction auction;
    private Item item;

    private ObservableList<Item> allAssets = FXCollections.observableArrayList();

    // CHỐT CHẶN: Cờ hiệu ngăn bão Event gửi trùng lệnh lên Server khi xóa/sửa dữ liệu tự động
    private boolean isUpdatingData = false;

    public void initialize() throws IOException {
        client.addListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        // Kết nối cột TableView
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("itemType"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("displayStatus"));

        filteredAssets = new FilteredList<>(allAssets, p -> true);
        tableProducts.setItems(filteredAssets);

        client.sendCommand(Command.SELECT_ITEMS, UserRole.ADMIN);

        // Lắng nghe sự kiện chọn dòng trên TableView
        tableProducts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // 1. Đồng bộ Model ngay lập tức
                item = newValue;
                auction = null; // Xóa dữ liệu cũ của phiên trước

                // 2. Làm mới giao diện chữ và ảnh lập tức
                showItemDetails(newValue);
                updateAuctionControls();

                // 3. Đưa biểu đồ về trạng thái chờ, tránh lưu ảnh biểu đồ của item cũ
                Platform.runLater(() -> {
                    bidLineChart.getData().clear();
                    bidLineChart.setTitle("Đang tải dữ liệu diễn biến giá...");
                });

                // 4. KIỂM TRA CỜ: Đẩy lệnh lấy dữ liệu từ Server trên một Thread riêng để không block UI
                if (!isUpdatingData) {
                    new Thread(() -> {
                        try {
                            Map<String, Object> setAuctionPayload = new HashMap<>();
                            setAuctionPayload.put("userId", p1.getUsername());
                            setAuctionPayload.put("itemId", newValue.getDatabaseId());

                            client.sendCommand(Command.SET_AUCTION, setAuctionPayload);
                            client.sendCommand(Command.GET_AUCTION, newValue.getDatabaseId());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                }
            } else {
                // Nếu không chọn dòng nào (hoặc bảng trống), xóa trắng giao diện chi tiết
                clearDetailsView();
            }
        });

        cbCategory.setItems(FXCollections.observableArrayList("TẤT CẢ", "Mỹ thuật", "Điện tử", "Phương tiện giao thông"));
        cbStatus.setItems(FXCollections.observableArrayList("TẤT CẢ", "DISABLE", "OPEN", "RUNNING", "FINISHED"));
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // TH1: Tải danh sách sản phẩm ban đầu
        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            List<Item> itemsFromServer = (List<Item>) response.payload();
            for (Item oldItem : allAssets) {
                AuctionEngine.getInstance().unwatchItem(oldItem);
            }

            Platform.runLater(() -> {
                allAssets.setAll(itemsFromServer);

                for (Item currentItem : allAssets) {
                    if (currentItem.getAuctionStatus() == null) {
                        currentItem.setDisplayStatus("DISABLE");
                    } else {
                        currentItem.setDisplayStatus(currentItem.getAuctionStatus().toString());

                        if (currentItem.getAuctionStatus() == AuctionStatus.OPEN ||
                                currentItem.getAuctionStatus() == AuctionStatus.RUNNING) {
                            updateSelectedItemStatus(currentItem);
                        }
                    }
                }

                // ĐỔI MỚI CHÍNH Ở ĐÂY: Khi mới vào ứng dụng, không tự động chọn dòng nào hết.
                // Cho TableView xóa chọn lựa và chủ động dọn trống giao diện chi tiết bên phải.
                tableProducts.getSelectionModel().clearSelection();
                clearDetailsView();
            });
        }

        // TH2: Nhận cập nhật trạng thái Item đơn lẻ từ server
        if (Command.ITEMS_UPDATE.equals(command)) {
            if (response.payload() instanceof Item) {
                Item newItem = (Item) response.payload();

                allAssets.stream()
                        .filter(it -> it.getDatabaseId() == newItem.getDatabaseId())
                        .findFirst()
                        .ifPresent(it -> AuctionEngine.getInstance().unwatchItem(it));

                Platform.runLater(() -> {
                    allAssets.removeIf(it -> it.getDatabaseId() == newItem.getDatabaseId());
                    allAssets.add(newItem);

                    if (newItem.getAuctionStatus() == null) {
                        newItem.setDisplayStatus("DISABLE");
                    } else {
                        updateSelectedItemStatus(newItem);
                    }

                    // Chỉ đồng bộ lại nếu Admin vẫn đang chọn đúng item vừa cập nhật này
                    if (item != null && item.getDatabaseId() == newItem.getDatabaseId()) {
                        item = newItem;
                        showItemDetails(newItem);
                        updateAuctionControls();
                    }
                });
            }
        }

        // TH3: Nhận thông tin phiên đấu giá cụ thể từ Server
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            Auction receivedAuction = (Auction) response.payload();
            Platform.runLater(() -> {
                // BIỆN PHÁP CHỐT CHẶN: Chỉ vẽ biểu đồ nếu dữ liệu trả về trùng khớp với sản phẩm đang được chọn
                if (receivedAuction != null && item != null && receivedAuction.getItemId() == item.getDatabaseId()) {
                    auction = receivedAuction;
                    updateBidChart(auction.getBidHistory());
                    updateAuctionControls();
                }
            });
        }

        if (Command.BID_UPDATE.equals(command)) {
            Map<String, Object> resData = (Map<String, Object>) response.payload();
            boolean success = (boolean) resData.getOrDefault("success", false);
            if (success) {
                Auction updatedAuction = (Auction) resData.get("auction");
                Platform.runLater(() -> {
                    // Chỉ cập nhật biểu đồ thời gian thực nếu người dùng đang xem chính sản phẩm này
                    if (updatedAuction != null && item != null && updatedAuction.getItemId() == item.getDatabaseId()) {
                        auction = updatedAuction;
                        updateBidChart(updatedAuction.getBidHistory());
                    }
                });
            }
        }

        // TH4: Kết quả Phê duyệt / Tạm dừng
        if (Command.SET_ALLOW_RESULT.equals(command)) {
            Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
            if (responsePayload == null) return;

            Object auctionObj = responsePayload.get("auction");
            boolean success = responsePayload.get("success") != null && (boolean) responsePayload.get("success");

            if (success && auctionObj instanceof Auction) {
                Auction updatedAuction = (Auction) auctionObj;

                if (item != null && updatedAuction.getItemId() == item.getDatabaseId()) {
                    if (auction != null) {
                        auction.setStatus(updatedAuction.getStatus());
                    }
                }

                for (Item it : allAssets) {
                    if (it.getDatabaseId() == updatedAuction.getItemId()) {
                        it.setAuctionStatus(updatedAuction.getStatus());
                        if (updatedAuction.getStatus() == null) {
                            AuctionEngine.getInstance().unwatchItem(it);
                        } else {
                            updateSelectedItemStatus(it);
                        }
                        break;
                    }
                }

                Platform.runLater(() -> {
                    for (Item it : allAssets) {
                        if (it.getDatabaseId() == updatedAuction.getItemId() && updatedAuction.getStatus() == null) {
                            it.setDisplayStatus("DISABLE");
                        }
                    }

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);

                    if (updatedAuction.getStatus() != null) {
                        alert.setContentText("Phê duyệt thành công!");
                    } else {
                        alert.setContentText("Tạm dừng thành công!");
                    }
                    alert.showAndWait();

                    if (item != null && item.getDatabaseId() == updatedAuction.getItemId()) {
                        showItemDetails(item);
                    }
                    updateAuctionControls();
                });
            }
        }

        // TH5: Xóa sản phẩm
        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            Map<String, Object> resData = (Map<String, Object>) response.payload();
            boolean success = (boolean) resData.getOrDefault("success", false);
            String message = (String) resData.getOrDefault("message", "");

            // KIỂM TRA FLAG: Nếu gói tin chứa 'isForceClose' thì đây là gói tin broadcast dành cho NGƯỜI XEM PHÒNG.
            // Admin là người chủ động xóa nên BỎ QUA gói tin này, không hiển thị Alert thất bại.
            boolean isForceClose = resData.containsKey("isForceClose") && (boolean) resData.get("isForceClose");
            if (isForceClose) {
                return; // Thoát ngay lập tức, nhường sân khấu cho gói tin phản hồi trực tiếp (success = true)
            }

            int deletedItemId = resData.get("deletedItemId") != null ? ((Number) resData.get("deletedItemId")).intValue() : -1;

            if (success && deletedItemId != -1) {
                allAssets.stream()
                        .filter(it -> it.getDatabaseId() == deletedItemId)
                        .findFirst()
                        .ifPresent(it -> AuctionEngine.getInstance().unwatchItem(it));
            }

            Platform.runLater(() -> {
                j_Delete.setDisable(false);

                if (success) {
                    isUpdatingData = true;

                    // 1. Thực hiện xóa dữ liệu cục bộ
                    allAssets.removeIf(it -> it.getDatabaseId() == deletedItemId);

                    // 2. Đưa UI về nền trắng sạch sẽ theo yêu cầu ban đầu
                    tableProducts.getSelectionModel().clearSelection();
                    item = null;
                    auction = null;
                    clearDetailsView();

                    isUpdatingData = false;

                    // 3. Hiện Alert thành công duy nhất
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);
                    alert.setContentText("Xóa sản phẩm thành công!");
                    alert.showAndWait();

                } else {
                    isUpdatingData = false;

                    // Alert thất bại thực sự (ví dụ: sản phẩm đã có người đặt giá)
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText("Xóa thất bại");
                    alert.setContentText(message);
                    alert.showAndWait();
                }
            });
        }
    }

    private void updateSelectedItemStatus(Item targetItem) {
        if (targetItem == null) return;

        AuctionEngine.getInstance().watchItem(targetItem, (auctionstatus, seconds) -> {
            if (auctionstatus == null || targetItem.getAuctionStatus() == null) {
                AuctionEngine.getInstance().unwatchItem(targetItem);
                Platform.runLater(() -> targetItem.setDisplayStatus("DISABLE"));
                return;
            }

            if (auctionstatus == AuctionStatus.FINISHED ||
                    auctionstatus == AuctionStatus.PAID ||
                    auctionstatus == AuctionStatus.CANCELLED) {

                AuctionEngine.getInstance().unwatchItem(targetItem);
                final String finalStatus = auctionstatus.toString();
                Platform.runLater(() -> targetItem.setDisplayStatus(finalStatus));
                return;
            }

            String timeFormatted = formatDuration(seconds);
            String statusString = switch (auctionstatus) {
                case OPEN -> "OPEN (" + timeFormatted + ")";
                case RUNNING -> "RUNNING (" + timeFormatted + ")";
                default -> auctionstatus.toString();
            };

            final String displayValue = statusString;
            Platform.runLater(() -> targetItem.setDisplayStatus(displayValue));
        });
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    private void showItemDetails(Item item) {
        if (item == null) {
            clearDetailsView();
            return;
        }

        j_Delete.setVisible(true);
        detailName.setText(item.getName());
        detailDesc.setText(getCustomDescription(item));


        // Xóa ảnh cũ trước khi nạp để tránh hiện tượng nháy ảnh cũ
        detailImage.setImage(null);

        if (item.getImg() != null && !item.getImg().trim().isEmpty()) {
            try {
                if (item.getImg().startsWith("http")) {
                    detailImage.setImage(new Image(item.getImg(), true));
                } else {
                    String imgPath = "/controller/img/" + item.getImg();
                    URL imgUrl = getClass().getResource(imgPath);
                    if (imgUrl != null) {
                        detailImage.setImage(new Image(imgUrl.toExternalForm(), true));
                    } else {
                        System.err.println("Không tìm thấy đường dẫn ảnh cục bộ: " + imgPath);
                    }
                }
            } catch (Exception e) {
                System.err.println("Lỗi nạp tệp ảnh sản phẩm: " + e.getMessage());
            }
        }
    }

    private void clearDetailsView() {
        detailName.setText("Chưa chọn sản phẩm"); // Sửa lại text cho hợp hoàn cảnh
        detailDesc.setText("Vui lòng chọn một sản phẩm từ danh sách bên cạnh để xem chi tiết thông tin và diễn biến giá.");
        detailSpecs.setText("");
        detailImage.setImage(null);
        bidLineChart.getData().clear();
        bidLineChart.setTitle("Không có dữ liệu");

        j_Delete.setVisible(false);
        j_ButtonController.setText("HỆ THỐNG ĐẤU GIÁ");
        j_ButtonController.setDisable(true);
        j_ButtonController.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
    }

    @FXML private Button j_ButtonController;

    @FXML
    void On_ButtonController(ActionEvent event) throws IOException {
        if (item == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", String.valueOf(item.getDatabaseId()));

        if (item.getAuctionStatus() == null) {
            payload.put("allow", "true");
        } else {
            payload.put("allow", "false");
        }
        client.sendCommand(Command.SET_ALLOW, payload);
    }

    private void updateAuctionControls() {
        if (item == null) {
            j_ButtonController.setDisable(true);
            j_ButtonController.setText("HỆ THỐNG ĐẤU GIÁ");
            j_ButtonController.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
            return;
        }

        j_ButtonController.setDisable(false);

        if (item.getAuctionStatus() == null) {
            j_ButtonController.setText("PHÊ DUYỆT");
            j_ButtonController.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
        } else if (item.getAuctionStatus() == AuctionStatus.FINISHED) {
            j_ButtonController.setText("ĐÃ KẾT THÚC");
            j_ButtonController.setDisable(true);
            j_ButtonController.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        } else if (item.getAuctionStatus() == AuctionStatus.PAID ||
                item.getAuctionStatus() == AuctionStatus.CANCELLED) {
            j_ButtonController.setText("HOÀN THÀNH");
            j_ButtonController.setDisable(true);
            j_ButtonController.setStyle("-fx-background-color: #9E9E9E; -fx-text-fill: white;");
        } else {
            j_ButtonController.setText("TẠM DỪNG");
            j_ButtonController.setStyle("-fx-background-color: #d35400; -fx-text-fill: white;");
        }
    }

    @FXML private LineChart<String, Number> bidLineChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    private void updateBidChart(List<BidTransaction> historyList) {
        Platform.runLater(() -> {
            bidLineChart.getData().clear();

            if (historyList == null || historyList.isEmpty()) {
                bidLineChart.setTitle("Chưa có lượt đấu giá nào");
                return;
            }

            String currentItemName = (item != null) ? item.getName() : detailName.getText();
            bidLineChart.setTitle("DIỄN BIẾN GIÁ: " + currentItemName);

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Giá đấu (VNĐ)");

            for (BidTransaction bid : historyList) {
                String timeStr = formatTime(bid.getBidTime());
                XYChart.Data<String, Number> data = new XYChart.Data<>(timeStr, bid.getAmount());
                series.getData().add(data);
            }

            bidLineChart.getData().add(series);

            for (XYChart.Data<String, Number> data : series.getData()) {
                int index = series.getData().indexOf(data);
                if (index < historyList.size()) {
                    BidTransaction bid = historyList.get(index);
                    Node node = data.getNode();

                    if (node != null) {
                        StackPane nodeStack = (StackPane) node;
                        nodeStack.setStyle("-fx-background-color: #f39c12, white; -fx-background-insets: 0, 1; -fx-background-radius: 50%;");
                        nodeStack.setPrefSize(12, 12);
                        nodeStack.setMinSize(12, 12);
                        nodeStack.setMaxSize(12, 12);
                        nodeStack.setCursor(Cursor.HAND);

                        String infoText = bid.getBidder() + ": " + String.format("%,.0f", bid.getAmount()) + " VNĐ";
                        Tooltip tooltip = new Tooltip(infoText);
                        tooltip.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 5px 10px;");

                        nodeStack.setOnMouseClicked(e -> {
                            if (tooltip.isShowing()) {
                                tooltip.hide();
                            } else {
                                Bounds bounds = nodeStack.localToScreen(nodeStack.getBoundsInLocal());
                                if (bounds != null) {
                                    tooltip.show(nodeStack, bounds.getMinX() - 20, bounds.getMinY() - 35);
                                }
                            }
                            e.consume();
                        });
                    }
                }
            }

            Node line = series.getNode().lookup(".chart-series-line");
            if (line != null) {
                line.setStyle("-fx-stroke-width: 2px; -fx-stroke: #f39c12;");
                line.setMouseTransparent(true);
            }

            for (Node n : bidLineChart.lookupAll(".chart-symbol")) {
                n.toFront();
            }
        });
    }

    private String formatTime(Instant instant) {
        return DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    @FXML
    void On_Filter(ActionEvent event) {
        Object selectedCategoryObj = cbCategory.getSelectionModel().getSelectedItem();
        Object selectedStatusObj = cbStatus.getSelectionModel().getSelectedItem();

        String selectedCategory = (selectedCategoryObj != null) ? selectedCategoryObj.toString() : null;
        String selectedStatus = (selectedStatusObj != null) ? selectedStatusObj.toString() : null;

        filteredAssets.setPredicate(item -> {
            boolean matchCategory = (selectedCategory == null || selectedCategory.isEmpty() || selectedCategory.equals("TẤT CẢ"));
            boolean matchStatus = (selectedStatus == null || selectedStatus.isEmpty() || selectedStatus.equals("TẤT CẢ"));

            if (item.getItemType() != null && selectedCategory != null && !selectedCategory.equals("TẤT CẢ")) {
                matchCategory = item.getItemType().equalsIgnoreCase(selectedCategory);
            }

            if (selectedStatus != null && !selectedStatus.equals("TẤT CẢ")) {
                if (item.getAuctionStatus() == null) {
                    matchStatus = selectedStatus.equalsIgnoreCase("DISABLE");
                } else {
                    matchStatus = item.getAuctionStatus().toString().equalsIgnoreCase(selectedStatus);
                }
            }

            return matchCategory && matchStatus;
        });
    }

    @FXML
    void On_ResetFilter(ActionEvent event) {
        cbCategory.getSelectionModel().clearSelection();
        cbStatus.getSelectionModel().clearSelection();
        filteredAssets.setPredicate(p -> true);
    }

    // --- Các FXML Components ---
    @FXML private ComboBox<String> cbCategory;
    @FXML private ComboBox<String> cbStatus;
    @FXML private TableColumn<?, ?> colCategory;
    @FXML private TableColumn<?, ?> colId;
    @FXML private TableColumn<?, ?> colName;
    @FXML private TableColumn<?, ?> colStatus;
    @FXML private TableView<Item> tableProducts;
    @FXML private Button j_Delete;
    @FXML private Button j_Return;
    @FXML private Button j_ItemManager;
    @FXML private Label j_LabelName;
    @FXML private ImageView j_image;

    @FXML private Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    @FXML private Label detailDesc;
    @FXML private ImageView detailImage;
    @FXML private Label detailName;
    @FXML private Label detailSpecs;

    @FXML void On_ItemManager(ActionEvent event) {}
    @FXML void On_MouseClickImg(MouseEvent event) {}

    @FXML
    void On_Return(ActionEvent event) {
        try {
            client.removeListener(this);
            SceneHelper.changeScene(j_Return, "/fxml/AdminView.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void On_Delete(ActionEvent event) throws IOException {
        if (item == null || isUpdatingData) {
            return;
        }

        isUpdatingData = true;
        j_Delete.setDisable(true);
        client.sendCommand(Command.DELETE_ITEM, item);
    }
    private String getCustomDescription(Item item) {
        if (item == null || item.getDescription() == null) {
            return "Không có mô tả cho sản phẩm này.";
        }

        String rawDesc = item.getDescription().trim();
        if (rawDesc.isEmpty()) {
            return "Không có mô tả cho sản phẩm này.";
        }

        // Nếu không bọc trong ngoặc nhọn JSON, hiển thị như text thường
        if (!rawDesc.startsWith("{") || !rawDesc.endsWith("}")) {
            return rawDesc;
        }

        try {
            // Tạo Map để chứa dữ liệu sau khi bóc tách từ JSON
            Map<String, String> map = new HashMap<>();

            // Loại bỏ dấu ngoặc nhọn { và } ở hai đầu
            String cleanDesc = rawDesc.substring(1, rawDesc.length() - 1);

            // Tách các cặp thuộc tính bằng dấu phẩy
            String[] pairs = cleanDesc.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length >= 2) {
                    // Lấy phần Key và loại bỏ toàn bộ dấu ngoặc kép '"' cũng như khoảng trắng
                    String key = keyValue[0].replace("\"", "").trim().toLowerCase();

                    // Lấy phần Value (gộp lại phòng trường hợp nội dung chứa dấu hai chấm ':')
                    StringBuilder valueBuilder = new StringBuilder();
                    for (int i = 1; i < keyValue.length; i++) {
                        if (i > 1) valueBuilder.append(":");
                        valueBuilder.append(keyValue[i]);
                    }
                    String value = valueBuilder.toString().replace("\"", "").trim();

                    map.put(key, value);
                }
            }

            // Lấy Loại sản phẩm chuẩn từ Enum có sẵn trong Model
            model.Items.ItemType type = item.getRawItemType();

            // Hiển thị thông tin chính xác theo từng danh mục sản phẩm
            if (type != null) {
                switch (type) {
                    case ART:
                        String artDesc = map.getOrDefault("description", "Không có mô tả");
                        String artist = map.getOrDefault("artist", "Không rõ");
                        return "Mô tả: " + artDesc + "\nHọa sĩ: " + artist;

                    case ELECTRONICS:
                        String brand = map.getOrDefault("brand", "Không rõ");
                        String model = map.getOrDefault("model", "Không rõ");
                        return "Thương hiệu: " + brand + "\nModel: " + model;

                    case VEHICLE:
                        String year = map.getOrDefault("year", "Không rõ");
                        String manufacturer = map.getOrDefault("manufacturer", "Không rõ");
                        return "Năm sản xuất: " + year + "\nNhà sản xuất: " + manufacturer;
                }
            }

            // Phương án dự phòng cuối cùng nếu không nhận diện được Type
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String keyFormatted = entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1);
                sb.append(keyFormatted).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            System.err.println("Lỗi xử lý cú pháp JSON mô tả: " + e.getMessage());
            return rawDesc; // Trả về chuỗi gốc từ DB để giao diện không bị trống
        }
    }
}