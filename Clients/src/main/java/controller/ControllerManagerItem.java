package controller;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.BidTransaction;
import network.*;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ControllerManagerItem implements ServerListener {
    private User p1 = UserSession.getLoggedInUser();
    private AuctionClient client = AuctionClient.getInstance();
    private Auction auction;
    // 1. Khai báo danh sách quan sát (Nằm ở ngoài hàm)
    private ObservableList<Item> allAssets = FXCollections.observableArrayList();
    public void initialize() throws IOException {
        client.setListener(this);
        // 1. Kết nối cột với dữ liệu (Tên phải khớp với phần sau chữ 'get' hoặc tên Property)
        colId.setCellValueFactory(new PropertyValueFactory<>("databaseId"));
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        // Cột này sẽ gọi hàm getItemType() trong class Item
        colCategory.setCellValueFactory(new PropertyValueFactory<>("itemType"));
        // Cột này sẽ gọi hàm displayStatusProperty() để tự cập nhật
        colStatus.setCellValueFactory(new PropertyValueFactory<>("displayStatus"));

        // 2. Quan trọng nhất: Gán danh sách vào bảng
        tableProducts.setItems(allAssets);
        client.sendCommand(Command.SELECT_ITEMS, "");
        tableProducts.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                showItemDetails(newValue);
                try {
                    client.sendCommand(Command.GET_AUCTION,newValue.getDatabaseId());
                    client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", newValue.getDatabaseId()));;
                    ItemSession.setLoggedInItem(newValue);
                } catch (IOException e) {throw new RuntimeException(e);}}
        });

    }


    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // TH1: Nhận danh sách tất cả sản phẩm (Lần đầu load)
        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            // Giả sử server gửi về một List<Item> trong DataPacket
            List<Item> itemsFromServer = (List<Item>) response.payload();
            Platform.runLater(() -> {
                allAssets.setAll(itemsFromServer);
                // BẮT ĐẦU THEO DÕI THỜI GIAN THỰC TẠI ĐÂY
                for (Item item : allAssets) {
                    AuctionEngine.getInstance().watchItem(item, (status, seconds) -> {
                        Platform.runLater(() -> {
                            // Tạo chuỗi hiển thị thời gian
                            String timeFormatted = formatDuration(seconds);
                            // Khởi tạo đối tượng Property (Lưu ý: StringProperty là abstract, dùng SimpleStringProperty)
                            String statusString = "";
                            switch (status) {
                                case OPEN: statusString = "OPEN (" + timeFormatted + ")"; break;
                                case RUNNING: statusString = "RUNNING (" + timeFormatted + ")"; break;
                                case FINISHED: statusString = "FINISHED"; break;
                                case PAID: statusString = "PAID"; break;
                                case CANCELED: statusString = "CANCELED"; break;
                            }

                            // Cập nhật vào thuộc tính hiển thị trên TableView
                            item.setDisplayStatus(statusString);
                            // Giả sử Item có thuộc tính displayStatus
                            // và setter tương ứng
                            // Đừng quên refresh bảng nếu bạn
                            // không dùng StringProperty binding
                        });
                    });
                };
             });
        }

        // TH2: Nhận tin nhắn cập nhật trạng thái từ Server (Broadcast cho 1 item)
        else if (Command.ITEMS_UPDATE.equals(command)) { // Thay thế bằng Command broadcast thật của bạn
            Item newItem = (Item) response.payload();
            allAssets.add(newItem);
        }
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            auction = (Auction) response.payload();
            // Xử lý dữ liệu phiên đấu giá nếu cần, ví dụ cập nhật chi tiết hiển thị
            // CẬP NHẬT BIỂU ĐỒ TẠI ĐÂY - Khi dữ liệu đã thực sự về tới Client
            Platform.runLater(() -> {
                if (auction != null) {
                    updateBidChart(auction.getBidHistory());
                }
            });
        }
        if (Command.SET_AUCTION_RESULT.equals(command)) {
            Map<String, Object> responsePayload = (Map) response.payload();
            auction =(Auction) responsePayload.get("auction");
            // Xử lý dữ liệu phiên đấu giá nếu cần, ví dụ cập nhật chi tiết hiển thị
            // CẬP NHẬT BIỂU ĐỒ TẠI ĐÂY - Khi dữ liệu đã thực sự về tới Client
            Platform.runLater(() -> {
                if (auction != null) {
                    updateBidChart(auction.getBidHistory());
                }
            });}
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


    @FXML
    private Button LogOut;
    @FXML
    private ComboBox<?> cbCategory;
    @FXML
    private ComboBox<?> cbStatus;
    @FXML
    private TableColumn<?, ?> colCategory;
    @FXML
    private TableColumn<?, ?> colId;
    @FXML
    private TableColumn<?, ?> colName;
    @FXML
    private TableColumn<?, ?> colStatus;
    @FXML
    private Label detailDesc;
    @FXML
    private ImageView detailImage;
    @FXML
    private Label detailName;
    @FXML
    private Label detailSpecs;
    @FXML
    private Button j_ItemManager;
    @FXML
    private Label j_LabelName;
    @FXML
    private ImageView j_image;
    @FXML
    private TableView<Item> tableProducts;

    @FXML
    void On_Filter(ActionEvent event) {

    }

    @FXML
    void On_ItemManager(ActionEvent event) {

    }

    @FXML
    void On_LogOut(ActionEvent event) {

    }

    @FXML
    void On_MouseClickImg(MouseEvent event) {

    }

    @FXML
    void On_ResetFilter(ActionEvent event) {

    }
    private void showItemDetails(Item item) {
        // 1. Cập nhật các Label chữ
        detailName.setText(item.getName());
        detailDesc.setText(item.getDescription());

        // Giả sử detailSpecs là nơi hiện giá và loại
        String specs = String.format("Loại: %s | Giá khởi điểm: %.2f VNĐ",
                item.getItemType(),
                item.getStartingPrice());
        detailSpecs.setText(specs);

        // 2. Cập nhật ảnh (Nếu bạn lưu img dưới dạng URL hoặc đường dẫn)
        if(item.getImg() != null && !item.getImg().isEmpty()){
            if (item.getImg().startsWith("http")) {
                detailImage.setImage(new Image(item.getImg(), true));
            } else {
                // Thử load từ resource (đảm bảo ảnh được copy vào target khi build)
                String imgPath = "/controller/img/" + item.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    detailImage.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }
    // 1. Đảm bảo khai báo đúng loại Axis
    @FXML
    private LineChart<String, Number> bidLineChart;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private NumberAxis yAxis;

    // 2. Hàm cập nhật biểu đồ (Gọi hàm này khi nhận được BidHistory từ Server)
    private void updateBidChart(List<BidTransaction> historyList) {
        // Xóa dữ liệu cũ
        bidLineChart.getData().clear();

        // Tạo đường dữ liệu mới
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Giá đấu");

        if (historyList == null || historyList.isEmpty()) {
            bidLineChart.setTitle("Chưa có lượt đấu giá nào");
            return;
        }

        bidLineChart.setTitle("DIỄN BIẾN GIÁ: " + detailName.getText());

        for (BidTransaction bid : historyList) {
            // Trục X truyền vào String (Thời gian), Trục Y truyền vào Double (Giá)
            series.getData().add(new XYChart.Data<>(
                    formatTime(bid.getBidTime()),
                    bid.getAmount()
            ));
        }

        bidLineChart.getData().add(series);
    }

    // 3. Hàm phụ trợ format thời gian (Ví dụ)
    private String formatTime(Instant instant) {
        return DateTimeFormatter.ofPattern("HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    public void On_UserManager(ActionEvent actionEvent) {
    }
}


