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

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ControllerManagerItem implements ServerListener {
    private FilteredList<Item> filteredAssets;
    private User p1 = UserSession.getLoggedInUser();
    private AuctionClient client = AuctionClient.getInstance();

    // Biến lưu phiên đấu giá hiện tại và item đang chọn
    private Auction auction;
    private Item item;

    // Danh sách quan sát hiển thị trên TableView
    private ObservableList<Item> allAssets = FXCollections.observableArrayList();

    public void initialize() throws IOException {
        client.setListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        // Kết nối cột TableView với dữ liệu model
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("itemType"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("displayStatus"));
        // Khởi tạo: Bản đầu hiện ra TẤT CẢ sản phẩm (vì điều kiện luôn luôn đúng 'true')
        filteredAssets = new FilteredList<>(allAssets, p -> true);
        tableProducts.setItems(filteredAssets); // Gán danh sách vào bảng


        client.sendCommand(Command.SELECT_ITEMS, UserRole.ADMIN);

        // Hiển thị chi tiết khi chọn một item trong bảng
        tableProducts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                item = newValue;
                auction = null; // Reset để tránh nhầm dữ liệu cũ

                showItemDetails(newValue);
                updateAuctionControls(); // Đặt nút về trạng thái "ĐANG TẢI"

                try {
                    // Gửi lệnh lấy thông tin phiên đấu giá của item vừa chọn
                    client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", newValue.getDatabaseId()));
                    client.sendCommand(Command.GET_AUCTION, newValue.getDatabaseId());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        cbCategory.setItems(FXCollections.observableArrayList("TẤT CẢ", "Mỹ thuật", "Điện tử", "Phương tiện giao thông"));
        cbStatus.setItems(FXCollections.observableArrayList("TẤT CẢ", "DISABLE", "OPEN", "RUNNING", "FINISHED"));
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // TH1: Nhận danh sách tất cả sản phẩm (Lần đầu load)
        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            List<Item> itemsFromServer = (List<Item>) response.payload();
            Platform.runLater(() -> {
                allAssets.setAll(itemsFromServer);

                // Kích hoạt theo dõi đếm ngược thời gian thực cho toàn bộ bảng
                for (Item currentItem : allAssets) {
                    if (currentItem.getAuctionStatus() == null) {
                        currentItem.setDisplayStatus("DISABLE");
                        continue;
                    }

                    AuctionEngine.getInstance().watchItem(currentItem, (status, seconds) -> {
                        Platform.runLater(() -> {
                            String timeFormatted = formatDuration(seconds);
                            if (status == null) {
                                currentItem.setDisplayStatus("DISABLE");
                                return; // Thoát luôn, không chạy xuống switch nữa
                            }
                            String statusString = switch (status) {
                                case OPEN -> "OPEN  (" + timeFormatted + ")";
                                case RUNNING -> "RUNNING (" + timeFormatted + ")";
                                case FINISHED -> "FINISHED";
                                case PAID -> "PAID";
                                case CANCELLED -> "CANCELLED";
                            };
                            currentItem.setDisplayStatus(statusString);
                        });
                    });
                }
            });
        }

        // TH2: Nhận sản phẩm mới hoặc cập nhật từ Server
        if (Command.ITEMS_UPDATE.equals(command)) {
            Item newItem = (Item) response.payload();
            Platform.runLater(() -> allAssets.add(newItem));
        }

        // TH3: Nhận dữ liệu phiên đấu giá của Item được chọn
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            auction = (Auction) response.payload();
            Platform.runLater(() -> {
                if (auction != null) {
                    updateBidChart(auction.getBidHistory());
                }
                updateAuctionControls();
            });
        }

        //Nhận kết quả sau khi Admin bấm phê duyệt/tạm dừng
        if (Command.SET_ALLOW_RESULT.equals(command)) {
            Map<String, Object> responsePayload = (Map<String, Object>) response.payload();
            Object auctionObj = responsePayload.get("auction");
            if (auctionObj instanceof Auction) {
                auction = (Auction) auctionObj;
            }

            Platform.runLater(() -> {
                if (auction != null) {
                    item.setAuctionStatus(auction.getStatus());
                    updateSelectedItemStatus(item);
                }
                updateAuctionControls();
            });
        }
        if(Command.DELETE_ITEM_RESULT.equals(command)){
            int result = (Integer) response.payload();
            if(result > 0){
                Platform.runLater(() -> {
                    tableProducts.getSelectionModel().clearSelection();
                    allAssets.remove(item);
                    item = null;
                    j_Delete.setVisible(false);
                    j_ButtonController.setText("ĐÃ XÓA");
                    j_ButtonController.setDisable(true);
                });
            } else {
                System.err.println("Xóa sản phẩm thất bại trên Server!");
        }
        }
    }

    // Cập nhật bộ đếm thời gian cho riêng Item đang được chọn (Tối ưu chỉ refresh bảng)
    private void updateSelectedItemStatus(Item targetItem) {
        if (targetItem == null) return;

        AuctionEngine.getInstance().watchItem(targetItem, (auctionstatus, seconds) -> {
            Platform.runLater(() -> {
                // 1. Kiểm tra an toàn: Nếu Engine báo trạng thái đã bị đưa về null
                if (auctionstatus == null) {
                    targetItem.setDisplayStatus("DISABLE");
                    return; // Dừng luôn, không chạy xuống switch-case nữa
                }

                // 2. Nếu trạng thái hợp lệ, tính toán chuỗi hiển thị như bình thường
                String timeFormatted = formatDuration(seconds);
                String statusString = switch (auctionstatus) {
                    case OPEN -> "OPEN (" + timeFormatted + "))";
                    case RUNNING -> "RUNNING (" + timeFormatted + ")";
                    case FINISHED -> "FINISHED";
                    case PAID -> "PAID";
                    case CANCELLED -> "CANCELLED";
                };

                targetItem.setDisplayStatus(statusString);
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
        // 1. Cập nhật các thành phần chữ tĩnh
        detailName.setText(item.getName());
        detailDesc.setText(item.getDescription());

        String specs = String.format("Loại: %s | Giá khởi điểm: %.2f VNĐ",
                item.getItemType(),
                item.getStartingPrice());
        detailSpecs.setText(specs);

        // 2. Cập nhật ảnh từ URL hoặc Resources
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
        if (auction == null) return;

        if (auction.getStatus() == null) {
            client.sendCommand(Command.SET_ALLOW, Map.of("itemId", String.valueOf(auction.getItemId()), "allow", "true"));
        } else {
            client.sendCommand(Command.SET_ALLOW, Map.of("itemId", String.valueOf(auction.getItemId()), "allow", "false"));
        }
    }

    // Hàm tập trung duy nhất chịu trách nhiệm thay đổi giao diện nút điều khiển
    private void updateAuctionControls() {
        if (auction == null) {
            j_ButtonController.setDisable(true);
            j_ButtonController.setText("ĐANG TẢI");
            return;
        }

        j_ButtonController.setDisable(false);
        if (auction.getStatus() == null) {
            j_ButtonController.setText("PHÊ DUYỆT");
        } else if (auction.getStatus() == AuctionStatus.FINISHED) {
            j_ButtonController.setText("ĐÃ KẾT THÚC");
            j_ButtonController.setDisable(true); // Kết thúc rồi thì khóa nút lại
        } else {
            j_ButtonController.setText("TẠM DỪNG");
        }
    }

    // --- Quản lý các thành phần Đồ họa / Biểu đồ ---
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



    // --- Bộ lọc các thông tin đấu giá ---
    @FXML
    void On_Filter(ActionEvent event) {
        // 1. Lấy giá trị Admin đang chọn tại thời điểm bấm nút
        Object selectedCategoryObj = cbCategory.getSelectionModel().getSelectedItem();
        Object selectedStatusObj = cbStatus.getSelectionModel().getSelectedItem();

        String selectedCategory = (selectedCategoryObj != null) ? selectedCategoryObj.toString() : null;
        String selectedStatus = (selectedStatusObj != null) ? selectedStatusObj.toString() : null;

        // 2. Kích hoạt chuyển đổi màng lọc
        filteredAssets.setPredicate(item -> {
            // Mặc định là thỏa mãn nếu Admin không chọn gì hoặc chọn "Tất cả"
            boolean matchCategory = (selectedCategory == null || selectedCategory.isEmpty() || selectedCategory.equals("TẤT CẢ"));
            boolean matchStatus = (selectedStatus == null || selectedStatus.isEmpty() || selectedStatus.equals("TẤT CẢ"));

            // Lọc theo Danh mục
            if (item.getItemType() != null && selectedCategory != null && !selectedCategory.equals("TẤT CẢ")) {
                matchCategory = item.getItemType().equalsIgnoreCase(selectedCategory);
            }

            // Lọc theo Trạng thái
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
        // 1. Xóa sạch các chữ đang chọn trên thanh ComboBox
        cbCategory.getSelectionModel().clearSelection();
        cbStatus.getSelectionModel().clearSelection();

        // 2. Đưa màng lọc về trạng thái ban đầu: Hiện ra TẤT CẢ sản phẩm
        filteredAssets.setPredicate(p -> true);
    }
    // --- Các Event Handler FXML chưa dùng tới ---
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

    }
    @FXML
    void On_Delete(ActionEvent event) throws IOException {
        client.sendCommand(Command.DELETE_ITEM,item.getDatabaseId());
    }
}