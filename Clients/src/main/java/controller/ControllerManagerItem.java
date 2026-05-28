package controller;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
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

    public void initialize() throws IOException {
        client.setListener(this);
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
                item = newValue;
                auction = null; // Xóa dữ liệu cũ của phiên trước

                showItemDetails(newValue);
                updateAuctionControls();

                try {
                    Map<String, Object> setAuctionPayload = new HashMap<>();
                    setAuctionPayload.put("userId", p1.getUsername());
                    setAuctionPayload.put("itemId", newValue.getDatabaseId());
                    client.sendCommand(Command.SET_AUCTION, setAuctionPayload);
                    client.sendCommand(Command.GET_AUCTION, newValue.getDatabaseId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
            Platform.runLater(() -> {
                // Hủy theo dõi các item cũ trước khi xóa danh sách để tránh rò rỉ bộ nhớ
                for (Item oldItem : allAssets) {
                    AuctionEngine.getInstance().unwatchItem(oldItem);
                }

                allAssets.setAll(itemsFromServer);

                // Kích hoạt theo dõi đếm ngược thời gian thực
                for (Item currentItem : allAssets) {
                    if (currentItem.getAuctionStatus() == null) {
                        currentItem.setDisplayStatus("DISABLE");
                        continue;
                    }

                    updateSelectedItemStatus(currentItem);
                }
            });
        }

        if (Command.ITEMS_UPDATE.equals(command)) {
            Item newItem = (Item) response.payload();
            Platform.runLater(() -> {
                // 1. Hủy theo dõi luồng cũ của item này (nếu có) để tránh rò rỉ bộ nhớ
                allAssets.stream()
                        .filter(it -> it.getDatabaseId() == newItem.getDatabaseId())
                        .findFirst()
                        .ifPresent(it -> AuctionEngine.getInstance().unwatchItem(it));

                // 2. Xóa phần tử cũ và thêm phần tử mới nhận từ server vào
                allAssets.removeIf(it -> it.getDatabaseId() == newItem.getDatabaseId());
                allAssets.add(newItem);

                // 3. CHỐT CHẶN: Ép trạng thái hiển thị thành DISABLE nếu status từ DB trả về là null
                if (newItem.getAuctionStatus() == null) {
                    newItem.setDisplayStatus("DISABLE");
                } else {
                    // Nếu sản phẩm mới mang trạng thái hoạt động (OPEN/RUNNING), kích hoạt luồng đếm ngược luôn
                    updateSelectedItemStatus(newItem);
                }

                // 4. Đồng bộ giao diện khu vực chi tiết bên phải nếu Admin đang chọn trúng item vừa cập nhật
                if (item != null && item.getDatabaseId() == newItem.getDatabaseId()) {
                    item = newItem;
                    showItemDetails(newItem);
                    updateAuctionControls();
                }
            });
        }

        // TH3: Nhận thông tin phiên đấu giá cụ thể
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            auction = (Auction) response.payload();
            Platform.runLater(() -> {
                if (auction != null) {
                    updateBidChart(auction.getBidHistory());
                }
                updateAuctionControls();
            });
        }

        // TH4: Kết quả Phê duyệt / Tạm dừng
        if (Command.SET_ALLOW_RESULT.equals(command)) {
            Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
            if (responsePayload == null) return;

            Object auctionObj = responsePayload.get("auction");
            boolean success = responsePayload.get("success") != null && (boolean) responsePayload.get("success");

            if (success && auctionObj instanceof Auction) {
                Auction updatedAuction = (Auction) auctionObj;

                // --- 1. XỬ LÝ LOGIC DỮ LIỆU TRÊN BACKGROUND THREAD ---
                if (item != null && updatedAuction.getItemId() == item.getDatabaseId()) {
                    if (auction != null) {
                        auction.setStatus(updatedAuction.getStatus());
                    }
                }

                for (Item it : allAssets) {
                    if (it.getDatabaseId() == updatedAuction.getItemId()) {
                        // Đồng bộ trạng thái cốt lõi từ server về model
                        it.setAuctionStatus(updatedAuction.getStatus());

                        if (updatedAuction.getStatus() == null) {
                            // Nếu đưa về null -> TẠM DỪNG: Ngắt luồng ngầm ngay lập tức tại đây
                            AuctionEngine.getInstance().unwatchItem(it);
                            Platform.runLater(() -> it.setDisplayStatus("DISABLE"));
                        } else {
                            // Nếu khác null -> PHÊ DUYỆT: Kích hoạt/Cập nhật lại luồng đếm giây
                            updateSelectedItemStatus(it);
                        }
                        break;
                    }
                }

                // --- 2. ĐẨY THÔNG BÁO UI LÊN FX APPLICATION THREAD ---
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);

                    // CHỐT CHẶN LỖI: Kiểm tra trực tiếp trên data sạch từ server trả về
                    if (auction.getStatus() != null) {
                        alert.setContentText("Phê duyệt thành công!");
                    } else {
                        alert.setContentText("Tạm dừng thành công!");
                    }
                    alert.showAndWait();

                    // Làm mới giao diện vùng bên phải và các nút bấm điều khiển
                    if (item != null && item.getDatabaseId() == updatedAuction.getItemId()) {
                        showItemDetails(item);
                    }
                    updateAuctionControls();
                });
            }
        }

        // TH5: Xóa sản phẩm thành công
        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            Map<String, Object> resData = (Map<String, Object>) response.payload();
            boolean success = (boolean) resData.getOrDefault("success", false);
            String message = (String) resData.getOrDefault("message", "");

            Platform.runLater(() -> {
                if (success) {
                    // ĐÃ SỬA LỖI TRỐNG KEY: Điền chính xác "deletedItemId"
                    int deletedItemId = ((Number) resData.get("deletedItemId")).intValue();

                    // Tìm và hủy watch của item bị xóa để giải phóng bộ nhớ thread
                    allAssets.stream()
                            .filter(it -> it.getDatabaseId() == deletedItemId)
                            .findFirst()
                            .ifPresent(it -> AuctionEngine.getInstance().unwatchItem(it));

                    allAssets.removeIf(it -> it.getDatabaseId() == deletedItemId);

                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);
                    alert.setContentText("Xóa thành công!");
                    alert.showAndWait();

                    if (item != null && item.getDatabaseId() == deletedItemId) {
                        tableProducts.getSelectionModel().clearSelection();
                        item = null;
                        auction = null;
                        j_Delete.setVisible(false);
                        j_ButtonController.setText("ĐÃ XÓA");
                        j_ButtonController.setDisable(true);
                    }
                } else {
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
            // --- 1. XỬ LÝ LOGIC & CHỐT CHẶN TRÊN BACKGROUND THREAD (Của Engine) ---

            // Chốt chặn 1: Trạng thái null (Tạm dừng)
            if (auctionstatus == null || targetItem.getAuctionStatus() == null) {
                AuctionEngine.getInstance().unwatchItem(targetItem); // Ngắt kết nối ngay lập tức tại đây
                Platform.runLater(() -> targetItem.setDisplayStatus("DISABLE"));
                return;
            }

            // Chốt chặn 2: Các trạng thái tĩnh (Kết thúc phiên)
            if (auctionstatus == AuctionStatus.FINISHED ||
                    auctionstatus == AuctionStatus.PAID ||
                    auctionstatus == AuctionStatus.CANCELLED) {

                AuctionEngine.getInstance().unwatchItem(targetItem); // Ngắt kết nối ngay lập tức tại đây
                final String finalStatus = auctionstatus.toString();
                Platform.runLater(() -> targetItem.setDisplayStatus(finalStatus));
                return;
            }

            // Tối ưu: Tính toán chuỗi hiển thị ngay trên Background Thread
            String timeFormatted = formatDuration(seconds);
            String statusString = switch (auctionstatus) {
                case OPEN -> "OPEN (" + timeFormatted + ")";
                case RUNNING -> "RUNNING (" + timeFormatted + ")";
                default -> auctionstatus.toString();
            };

            // --- 2. ĐẨY DỮ LIỆU ĐÃ XỬ LÝ XONG LÊN UI THREAD ĐỂ HIỂN THỊ ---
            final String displayValue = statusString;
            Platform.runLater(() -> {
                targetItem.setDisplayStatus(displayValue);
            });
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
        if (item == null) return;
        j_Delete.setVisible(true);
        detailName.setText(item.getName());
        detailDesc.setText(item.getDescription());

        String specs = String.format("Loại: %s | Giá khởi điểm: %.2f VNĐ",
                item.getItemType(),
                item.getStartingPrice());
        detailSpecs.setText(specs);

        if (item.getImg() != null && !item.getImg().isEmpty()) {
            if (item.getImg().startsWith("http")) {
                detailImage.setImage(new Image(item.getImg(), true));
            } else {
                String imgPath = "/controller/img/" + item.getImg();
                URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    detailImage.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }

    @FXML
    private Button j_ButtonController;

    @FXML
    void On_ButtonController(ActionEvent event) throws IOException {
        if (item == null) return; // Bảo vệ an toàn bằng cách check trực tiếp đối tượng Item được chọn

        Map<String, Object> payload = new HashMap<>();
        payload.put("itemId", String.valueOf(item.getDatabaseId()));

        // Gửi lệnh đảo ngược trạng thái chuẩn xác dựa theo UI TableView
        if (item.getAuctionStatus() == null) {
            payload.put("allow", "true"); // Đang DISABLE thì bấm nút sẽ gửi lệnh PHÊ DUYỆT
        } else {
            payload.put("allow", "false"); // Đang OPEN/RUNNING thì bấm nút sẽ gửi lệnh TẠM DỪNG
        }
        client.sendCommand(Command.SET_ALLOW, payload);
    }

    private void updateAuctionControls() {
        // ĐÃ SỬA LỖI ĐỒNG BỘ: Dùng thuộc tính item làm gốc duy nhất để render chữ của nút bấm
        if (item == null) {
            j_ButtonController.setDisable(true);
            j_ButtonController.setText("ĐANG TẢI");
            return;
        }

        j_ButtonController.setDisable(false);

        if (item.getAuctionStatus() == null) {
            j_ButtonController.setText("PHÊ DUYỆT");
            j_ButtonController.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;"); // Màu xanh lá bắt mắt
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
            // Trạng thái OPEN hoặc RUNNING
            j_ButtonController.setText("TẠM DỪNG");
            j_ButtonController.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;"); // Đổi sang màu đỏ cảnh báo khi chạy
        }
    }

    @FXML private LineChart<String, Number> bidLineChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    private void updateBidChart(List<BidTransaction> historyList) {
        bidLineChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Giá đấu");

        if (historyList == null || historyList.isEmpty()) {
            bidLineChart.setTitle("Chưa có lượt đấu giá nào");
            return;
        }

        bidLineChart.setTitle("DIỄN BIẾN GIÁ: " + detailName.getText());
        for (BidTransaction bid : historyList) {
            series.getData().add(new XYChart.Data<>(
                    formatTime(bid.getBidTime()),
                    bid.getAmount()
            ));
        }
        bidLineChart.getData().add(series);
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
            SceneHelper.changeScene(j_Return, "/fxml/AdminView.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    void On_Delete(ActionEvent event) throws IOException {
        if (item != null) {
            client.sendCommand(Command.DELETE_ITEM, item);
        }
    }
}