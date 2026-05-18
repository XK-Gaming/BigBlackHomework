package controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.AuctionStatus;
import model.auction.BidTransaction;
import network.AuctionClient;
import network.Command;
import network.ServerListener;
import network.DataPacket;
import network.AuctionEngine;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ControllerAuction implements ServerListener {
    private AuctionClient client = AuctionClient.getInstance();
    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();
    User p1 = UserSession.getLoggedInUser();
    Item item1 = ItemSession.getLoggedInItem();
    static Auction this_Auction;
    private String watchToken;
    private boolean finishHandled;

    public void initialize() throws IOException {
        client.setListener(this);
        showSessionProductAndLoadingAuctionState();
        client.sendCommand(Command.GET_AUCTION, item1 != null ? item1.getDatabaseId() : null);
        client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", item1.getDatabaseId()));
    }

    /**
     * Tránh nháy UI: điền ngay thông tin sản phẩm đã có từ {@link ItemSession};
     * phần trạng thái đấu giá / dẫn đầu chờ {@code GET_AUCTION_RESULT}.
     */
    private void showSessionProductAndLoadingAuctionState() {
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
        }
        if (item1 != null) {
            j_name.setText(item1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
            j_description.setText(item1.getDescription() != null ? item1.getDescription() : "");
            renderImage();
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        }
        j_status.setText("Đang tải phiên đấu giá...");
        j_status.setTextFill(Color.web("#bdc3c7"));
        j_leadingBidder.setText("—");
        if (j_countdown != null) {
            j_countdown.setVisible(false);
        }
        j_apply.setDisable(true);
        j_notified.setVisible(false);
    }

    public void onAuctionDataLoaded(Auction auction) {
        this_Auction = auction;
        Platform.runLater(() -> {
            if (this_Auction == null) {
                System.out.println("⚠ Cảnh báo: Không tìm thấy phiên đấu giá cho sản phẩm này!");

                // Vẫn hiển thị thông tin cơ bản của sản phẩm
                j_LabelName.setText(p1.getName());
                j_name.setText(item1.getName());
                renderImage();
                j_description.setText(item1.getDescription());

                // Khoá chức năng đấu giá lại vì chưa có phiên
                j_status.setText("CHƯA DIỄN RA");
                j_apply.setDisable(true);
                j_notified.setText("Sản phẩm này hiện chưa được mở bán.");
                j_notified.setVisible(true);

                updatePriceAndLeader(); // Cập nhật giá gốc
                return; // DỪNG LẠI NGAY, KHÔNG GỌI setupUI() NỮA ĐỂ TRÁNH LỖI
            }

            try {
                setupUI();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void setupUI() throws SQLException {
        j_status.setTextFill(Color.web("#f1c40f"));
        if (j_countdown != null) {
            j_countdown.setVisible(true);
        }
        j_LabelName.setText(p1.getName());
        j_name.setText(item1.getName());
        renderImage();
        j_description.setText(item1.getDescription());
        updatePriceAndLeader();
        startStatusEngine();
    }

    private void updatePriceAndLeader() {
        if (this_Auction == null) {
            // Nếu chưa có Auction, hiển thị giá từ ItemSession
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
            j_leadingBidder.setText("—");
            return;
        }

        // ✅ ƯU TIÊN lấy tất cả dữ liệu từ this_Auction vì nó vừa được load từ DB
        DecimalFormat df = new DecimalFormat("#,###");

        // Cập nhật lại giá cho item1 để đồng bộ các hàm khác

        j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");

        // Lấy tên người dẫn đầu
        String leader = this_Auction.getLeadingBidder();

        if (leader == null || leader.trim().isEmpty() || leader.equalsIgnoreCase("null")) {
            j_leadingBidder.setText("Chưa có");
        } else {
            j_leadingBidder.setText(leader.replace("\"", ""));
        }
    }

    private void renderImage() {
        if (item1.getImg() != null && !item1.getImg().isEmpty()) {
            if (item1.getImg().startsWith("http")) {
                j_img.setImage(new Image(item1.getImg()));
            } else {
                // Thử load từ resource trước
                String imgPath = "/controller/img/" + item1.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }

    @FXML
    private TextField j_setPrice;

    @FXML
    private Button j_apply;

    @FXML
    private Label j_leadingBidder;

    @FXML
    void On_apply(ActionEvent event) throws IOException {
        j_notified.setVisible(false);

        String priceText = j_setPrice.getText();
        if (priceText == null || priceText.isEmpty()) {
            j_notified.setText("Vui lòng nhập giá đặt!");
            j_notified.setVisible(true);
            return;
        } else if (Double.parseDouble(priceText) <=  item1.getCurrentHighestPrice() || Double.parseDouble(priceText) > p1.getBalance()) {
            j_notified.setText("Đặt giá không hợp lệ");
            j_notified.setVisible(true);
            return;
        }else{

        client.sendCommand(Command.BID, Map.of(
                "itemId", item1.getDatabaseId(),
                "bidderId", p1.getUsername(),
                "amount", priceText
        ));}
    }

    @FXML
    private AnchorPane Pane1;

    @FXML
    private Label j_LabelName;

    @FXML
    private Label j_days;

    @FXML
    private Label j_description;

    @FXML
    private ImageView j_image;

    @FXML
    private ImageView j_img;

    @FXML
    private Label j_name;

    @FXML
    private Button j_return;

    @FXML
    private Label j_textSoDu;

    @FXML
    void On_MouseClickImg(MouseEvent event) {

    }

    @FXML
    void On_Return(ActionEvent event) throws IOException {
        cleanup();
        SceneHelper.changeScene(j_return, "View3.fxml");
        ItemSession.cleanItemSession();
        client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", ""));

    }

    @FXML
    private Label j_CurrentPrice;

    @FXML
    private Label j_notified;

    private void updateTimerLabels(long current) {
        long d = current / 86400;
        long h = (current % 86400) / 3600;
        long m = (current % 3600) / 60;
        long s = current % 60;

        j_days.setText(String.format("%02d", d));
        j_hours.setText(String.format("%02d", h));
        j_mins.setText(String.format("%02d", m));
        j_secs.setText(String.format("%02d", s));
    }

    @FXML
    private Label j_mins;

    @FXML
    private Label j_hours;

    @FXML
    private Label j_secs;

    @FXML
    private Label j_status;

    @FXML
    private HBox j_countdown;

    public void cleanup() {
        if (watchToken != null) {
            auctionEngine.unwatch(watchToken);
            watchToken = null;
        }
    }

    private void startStatusEngine() {
        cleanup();
        finishHandled = false;
        watchToken = auctionEngine.watchItem(item1, (status, secondsToNextChange) -> Platform.runLater(() -> {
            if (this_Auction != null) {
                this_Auction.setStatus(status);
            }
            updateTimerLabels(secondsToNextChange);
            switch (status) {
                case OPEN -> {
                    j_status.setText("CHƯA DIỄN RA");
                    j_apply.setDisable(true);
                }
                case RUNNING -> {
                    j_status.setText("ĐANG DIỄN RA");
                    j_apply.setDisable(false);
                }
                case FINISHED -> {
                    j_status.setText("ĐÃ KẾT THÚC");
                    j_apply.setDisable(true);
                    if (!finishHandled) {
                        finishHandled = true;
                        handleFinishedAuction();
                    }
                }
                case PAID, CANCELED -> {
                    j_apply.setDisable(true);
                    j_status.setText(status == AuctionStatus.PAID ? "ĐÃ THANH TOÁN" : "ĐÃ HỦY");
                }
            }
        }));
    }

    private void handleFinishedAuction() {
        if (this_Auction != null && this_Auction.getLeadingBidder() != null
                && this_Auction.getLeadingBidder().equals(p1.getUsername())) {
            j_notified.setText("Chúc mừng! Bạn đã thắng. Đang chuyển đến trang thanh toán...");
            j_notified.setVisible(true);
            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(e -> SceneHelper.changeScene(j_apply, "ViewPaid.fxml"));
            delay.play();
        } else {
            j_notified.setText("Phiên đấu giá đã kết thúc.");
            j_notified.setVisible(true);
        }
    }

    // 1. Đảm bảo khai báo đúng loại Axis
    @FXML
    private LineChart<String, Number> bidLineChart;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private NumberAxis yAxis;
    private void updateBidChart(List<BidTransaction> historyList) {
        Platform.runLater(() -> {
            bidLineChart.getData().clear();
            if (historyList == null || historyList.isEmpty()) {
                bidLineChart.setTitle("Chưa có lượt đấu giá nào");
                return;
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Giá đấu (VNĐ)");

            for (BidTransaction bid : historyList) {
                String timeStr = formatTime(bid.getBidTime());
                XYChart.Data<String, Number> data = new XYChart.Data<>(timeStr, bid.getAmount());

                data.nodeProperty().addListener((ov, oldNode, newNode) -> {
                    if (newNode != null) {
                        StackPane nodeStack = (StackPane) newNode;

                        // 1. STYLE NÚT CHUẨN (Nhỏ, tròn, không bao giờ bị phình)
                        String defaultStyle = "-fx-background-color: #f39c12, white; -fx-background-insets: 0, 1; -fx-background-radius: 50%;";
                        nodeStack.setStyle(defaultStyle);
                        nodeStack.setPrefSize(12, 12);
                        nodeStack.setMinSize(12, 12);
                        nodeStack.setMaxSize(12, 12);
                        nodeStack.setCursor(Cursor.HAND);

                        // 2. SỬ DỤNG TOOLTIP THAY VÌ LABEL
                        String infoText = bid.getBidder() + ": " + String.format("%,.0f", bid.getAmount()) + " VNĐ";
                        Tooltip tooltip = new Tooltip(infoText);
                        tooltip.setStyle("-fx-background-color: #2c3e50; -fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 5px 10px;");

                        // (Tùy chọn) Nếu bạn muốn chỉ cần CẦM CHUỘT DI VÀO LÀ HIỆN, hãy mở comment dòng dưới:
                        // Tooltip.install(nodeStack, tooltip);

                        // 3. SỰ KIỆN CLICK ĐỂ HIỆN TOOLTIP
                        nodeStack.setOnMouseClicked(e -> {
                            if (tooltip.isShowing()) {
                                tooltip.hide();
                            } else {
                                // Lấy tọa độ tuyệt đối của nút trên màn hình để đặt Tooltip
                                Bounds bounds = nodeStack.localToScreen(nodeStack.getBoundsInLocal());
                                if (bounds != null) {
                                    // Hiển thị tooltip hơi xê dịch lên trên một chút (-30px)
                                    tooltip.show(nodeStack, bounds.getMinX() - 20, bounds.getMinY() - 35);
                                }
                            }
                            e.consume(); // Chặn sự kiện
                        });
                    }
                });

                series.getData().add(data);
            }

            bidLineChart.getData().add(series);

            // 4. LÀM MẢNH ĐƯỜNG NỐI VÀ CHỐNG CHẶN CHUỘT
            Platform.runLater(() -> {
                Node line = series.getNode().lookup(".chart-series-line");
                if (line != null) {
                    line.setStyle("-fx-stroke-width: 1.5px; -fx-stroke: #f39c12;");
                    line.setMouseTransparent(true); // Để chuột click xuyên qua đường nối trúng vào nút
                }

                // Đưa tất cả các nút lên trên cùng
                for (Node n : bidLineChart.lookupAll(".chart-symbol")) {
                    n.toFront();
                }
            });
        });
    }
    // 3. Hàm phụ trợ format thời gian (Ví dụ)
    private String formatTime(Instant instant) {
        // Thêm ngày/tháng vào trước giờ:phút:giây
        return DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.getCommand();

        if (Command.GET_AUCTION_RESULT.equals(command)) {
            this_Auction = (Auction) response.getPayload();
            onAuctionDataLoaded(this_Auction);
            Platform.runLater(() -> {
                if (this_Auction != null) {
                    updateBidChart(this_Auction.getBidHistory());
                } else{
                    j_notified.setText("Phiên đấu giá không tồn tại hoặc đã bị xóa.");
                    j_notified.setVisible(true);
                    j_status.setText("KHÔNG TỒN TẠI");
                    j_status.setVisible(true);
                }
            });
        }
        if (Command.BID_UPDATE.equals(command)) {
            Map<String, Object> update = (Map<String, Object>) response.getPayload();
            String itemId = String.valueOf(update.get("itemId"));
            if (item1 == null || !String.valueOf(item1.getDatabaseId()).equals(itemId)) {
                return;
            }
            this_Auction = (Auction) update.get("auction");


            Platform.runLater(() -> {
                if (this_Auction != null) {
                    try{
                    updateBidChart(this_Auction.getBidHistory());
                    if(this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 2)!= null){
                    String oldBidder =this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 2).getBidder();
                    if(p1.getUsername().equals(oldBidder)){
                        double oldPrice = this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 2).getAmount();
                        p1.setBalance(p1.getBalance() + oldPrice);
                        DecimalFormat df = new DecimalFormat("#,###");
                        j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
            ;};

                }}catch (Exception e){
                }
                }
                Object newPriceObj = update.get("newPrice");
                if (newPriceObj instanceof Number) {
                    item1.setCurrentHighestPrice(((Number) newPriceObj).doubleValue());
                }
                Object auctionObj = update.get("auction");
                if (auctionObj instanceof Auction) {
                    this_Auction = (Auction) auctionObj;
                }
                // ✅ FIX: Đảm bảo leadingBidder được set từ bidderId trong BID_UPDATE
                String bidderId = String.valueOf(update.get("bidderId"));
                if (this_Auction != null && bidderId != null && !bidderId.isEmpty()) {
                    this_Auction.setLeadingBidder(bidderId);
                }
                updatePriceAndLeader();
                j_notified.setText("Có lượt đặt giá mới trong phiên.");
                j_notified.setVisible(true);
            });
        }
        if (Command.BID_RESULT.equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");
            Platform.runLater(() -> {
                if (isSuccess) {
                    j_notified.setText("Đấu giá thành công");
                    p1.setBalance(p1.getBalance() - Double.parseDouble(j_setPrice.getText()));
                    DecimalFormat df = new DecimalFormat("#,###");
                    j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
                } else {
                    j_notified.setText(message);
                }
                j_notified.setVisible(true);
            });
        }
        if (Command.SET_ALLOW_RESULT.equals(command)) {
            j_notified.setText("Phiên đấu đã tạm dừng");
            j_status.setText("CHƯA PHÊ DUYỆT");
        }
        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            j_notified.setText("Sản phẩm đã bị xóa");
            j_status.setText("KHÔNG TỒN TẠI");
        }
    }
}
