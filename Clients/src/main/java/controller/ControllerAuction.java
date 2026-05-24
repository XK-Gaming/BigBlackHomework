package controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import javafx.scene.layout.*;
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
import org.controlsfx.control.Notifications;

import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
    import javafx.animation.PauseTransition;
    import javafx.application.Platform;
    import javafx.event.ActionEvent;
    import javafx.fxml.FXML;
    import javafx.scene.Cursor;
    import javafx.scene.Node;
    import javafx.scene.chart.CategoryAxis;
    import javafx.scene.chart.LineChart;
    import javafx.scene.chart.NumberAxis;
    import javafx.scene.chart.XYChart;
    import javafx.scene.control.Button;
    import javafx.scene.control.Label;
    import javafx.scene.control.TextField;
    import javafx.scene.image.Image;
    import javafx.scene.image.ImageView;
    import javafx.scene.input.MouseEvent;
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
        private final AuctionClient client = AuctionClient.getInstance();
        private final AuctionEngine auctionEngine = AuctionEngine.getInstance();
        User p1 = UserSession.getLoggedInUser();
        Item item1 = ItemSession.getLoggedInItem();
        static Auction this_Auction;
        private String watchToken;
        private boolean finishHandled;

        public void initialize() throws IOException {
            client.setListener(this);
            statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
            statusManager.startMonitoring();
            
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

        private void syncAuctionSnapshot(Auction auction) {
            if (auction == null || item1 == null) {
                return;
            }
            this_Auction = auction;
            Item auctionItem = auction.getItem();
            if (auctionItem == null) {
                return;
            }
            item1.setCurrentHighestPrice(auctionItem.getCurrentHighestPrice());
            if (auctionItem.getAuctionStartTime() != null) {
                item1.setAuctionStartTime(auctionItem.getAuctionStartTime());
            }
            if (auctionItem.getAuctionEndTime() != null) {
                item1.setAuctionEndTime(auctionItem.getAuctionEndTime());
                finishHandled = false;
            }
        }

        private void syncAuctionEndTime(Object endTimeValue) {
            if (item1 == null || !(endTimeValue instanceof Instant auctionEndTime)) {
                return;
            }
            item1.setAuctionEndTime(auctionEndTime);
            finishHandled = false;
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

            if (priceText == null || priceText.trim().isEmpty()) {
                j_notified.setText("Vui lòng nhập giá đặt!");
                j_notified.setVisible(true);
                return;
            }

            try {
                // Sử dụng Long thay vì Double để ép người dùng nhập số nguyên (VNĐ)
                long bidAmount = Long.parseLong(priceText);

                if (bidAmount <= item1.getCurrentHighestPrice()) {
                    j_notified.setText("Giá đặt phải lớn hơn giá hiện tại!");
                    j_notified.setVisible(true);
                } else if (bidAmount > p1.getBalance()) {
                    j_notified.setText("Số dư không đủ để đấu giá");
                    j_notified.setVisible(true);
                } else {
                    client.sendCommand(Command.BID, Map.of(
                            "itemId", item1.getDatabaseId(),
                            "bidderId", p1.getUsername(),
                            "amount", priceText
                    ));
                }
            } catch (NumberFormatException e) {
                // Nếu nhập 150000.5 hoặc chuỗi chữ, app sẽ nhảy vào đây chứ không bị crash
                j_notified.setText("Giá đặt phải là số nguyên hợp lệ (Không chứa dấu thập phân)!");
                j_notified.setVisible(true);
            }
        }

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
            SceneHelper.changeScene(j_return, "/fxml/BidderView.fxml");
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
    private javafx.scene.shape.Circle connectionStatus;

    @FXML
    private Label connectionText;

    private ConnectionStatusManager statusManager;

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
                    case PAID, CANCELLED -> {
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
                delay.setOnFinished(e -> SceneHelper.changeScene(j_apply, "PayingView.fxml"));
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

        private void handleIncomingToastNotification(Object payload) {
            DecimalFormat df = new DecimalFormat("#,###");
            try {
                // 1. Giải mã gói tin từ Server an toàn
                Map<String, Object> notifData = (Map<String, Object>) payload;

                double newPrice = 0;
                Object priceObj = notifData.get("newPrice");
                if (priceObj instanceof Number) {
                    newPrice = ((Number) priceObj).doubleValue();
                }

                Item item = (Item) notifData.get("item");
                final double finalPrice = newPrice;

                // 2. Đẩy việc hiển thị lên UI Thread của JavaFX
                Platform.runLater(() -> {

                    // [THAY ĐỔI]: Tạo Layout chính ôm nội dung, bỏ viền và bóng đổ để hòa làm một với khung ngoài
                    HBox customToast = new HBox();
                    customToast.setAlignment(Pos.CENTER_LEFT);
                    customToast.setPrefWidth(300); // Thu nhỏ lại một chút để vừa vặn với khung chứa của ControlsFX
                    customToast.setStyle("-fx-background-color: #FFFFFF;"); // Chỉ cần nền trắng đơn giản

                    // 3. Khối Icon bên trái (Nền màu xanh dương đậm)
                    StackPane iconBlock = new StackPane();
                    iconBlock.setPrefSize(60, 70);
                    iconBlock.setStyle("-fx-background-color: #1565C0;"); // Khung ngoài sẽ tự bo góc nên ở đây để vuông

                    Label icon = new Label("🔔");
                    icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                    iconBlock.getChildren().add(icon);

                    // 4. Phần chữ hiển thị (VBox) ở giữa
                    VBox textContainer = new VBox();
                    textContainer.setSpacing(4);
                    textContainer.setAlignment(Pos.CENTER_LEFT);
                    textContainer.setPadding(new Insets(10, 10, 10, 15));
                    HBox.setHgrow(textContainer, Priority.ALWAYS);

                    Label titleLabel = new Label("SẢN PHẨM CÓ LƯỢT ĐẤU GIÁ MỚI!");
                    titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

                    Label messageLabel = new Label("Sản phẩm " + (item != null ? item.getName() : "") + " : " + df.format(finalPrice) + " VNĐ");
                    messageLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', Arial;");
                    messageLabel.setWrapText(true);
                    messageLabel.setMaxWidth(200);

                    textContainer.getChildren().addAll(titleLabel, messageLabel);

                    // [THAY ĐỔI]: Chỉ thêm khối icon và khối chữ vào layout (Đã loại bỏ nút x tự chế)
                    customToast.getChildren().addAll(iconBlock, textContainer);

                    // 5. Khởi tạo ControlsFX và tận dụng hệ thống mặc định
                    Notifications notificationBuilder = Notifications.create()
                            .owner(j_textSoDu) // Neo theo ứng dụng của bạn
                            .graphic(customToast) // Nhúng nội dung custom vào
                            .hideAfter(Duration.seconds(4)) // Tự động ẩn sau 4 giây
                            .position(Pos.BOTTOM_RIGHT); // Xuất hiện góc dưới bên phải

                    // [MẸO ĐẸP]: Xóa bỏ padding thừa của khung ngoài để khối màu xanh sát rạt ra rìa trái
                    customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                        if (newScene != null) {
                            newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                                if (newWin != null) {
                                    javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                    if (notificationPopup != null) {
                                        // Ép padding về 0 để phần màu xanh bám sát viền trái ngoài cùng
                                        notificationPopup.setStyle("-fx-padding: 0;");
                                    }
                                }
                            });
                        }
                    });

                    // Hiển thị thông báo lên màn hình
                    notificationBuilder.show();
                });

        } catch (Exception e) {
            System.err.println("Lỗi khi hiển thị Toast Notification: " + e.getMessage());
        }
    }
    @Override
    public void onServerResponse(DataPacket response) {
        DecimalFormat df = new DecimalFormat("#,###");
        Command command = response.command();

        if (Command.GET_AUCTION_RESULT.equals(command)) {
            this_Auction = (Auction) response.payload();
            onAuctionDataLoaded(this_Auction);
            syncAuctionSnapshot(this_Auction);

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
            Map<String, Object> update = (Map<String, Object>) response.payload();
            String itemId = String.valueOf(update.get("itemId"));
            if (item1 == null || !String.valueOf(item1.getDatabaseId()).equals(itemId)) {
                return;
            }
            this_Auction = (Auction) update.get("auction");


                    Platform.runLater(() -> {
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
                        if (auctionObj instanceof Auction) {
                            syncAuctionSnapshot((Auction) auctionObj);
                        }
                        syncAuctionEndTime(update.get("auctionEndTime"));

                        if (this_Auction != null && bidderId != null && !bidderId.isEmpty() && !bidderId.equals("null")) {
                            this_Auction.setLeadingBidder(bidderId);
                        }

                        if (this_Auction != null) {
                            try{
                            updateBidChart(this_Auction.getBidHistory());
                            int size = this_Auction.getBidHistory().size();
                            String oldBidder;
                            String newBidder = this_Auction.getLeadingBidder();
                            if(p1.getUsername().equals(newBidder)){
                                double newPrice = this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 1).getAmount();
                                p1.setBalance(p1.getBalance() - newPrice);
                            }
                            else {
                                j_notified.setText("Có lượt đặt giá mới trong phiên.");
                                j_notified.setVisible(true);
                                javax.swing.Timer timer = new javax.swing.Timer(4000, new java.awt.event.ActionListener() {
                                    @Override
                                    public void actionPerformed(java.awt.event.ActionEvent e) {
                                        j_notified.setVisible(false);}});
                                timer.setRepeats(false);
                                timer.start();}
                            if(size > 1){
                                oldBidder = this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 2).getBidder();
                                if(p1.getUsername().equals(oldBidder)){
                                    double oldPrice = this_Auction.getBidHistory().get(this_Auction.getBidHistory().size() - 2).getAmount();
                                    p1.setBalance(p1.getBalance() + oldPrice);}
                            }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        updatePriceAndLeader();
                        j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");

                    });
                }


            if (Command.BID_RESULT.equals(command)) {
                if (response.payload() instanceof Map) {
                    Map<String, Object> result = (Map<String, Object>) response.payload();
                    boolean isSuccess = (boolean) result.get("success");
                    String message = (String) result.get("message");
                    Platform.runLater(() -> {
                        if (isSuccess) {
                            syncAuctionEndTime(result.get("auctionEndTime"));
                            j_notified.setText("Đấu giá thành công");
                        } else {
                            j_notified.setText(message);
                        }
                        j_notified.setVisible(true);
                        javax.swing.Timer timer = new javax.swing.Timer(4000, new java.awt.event.ActionListener() {
                            @Override
                            public void actionPerformed(java.awt.event.ActionEvent e) {
                                j_notified.setVisible(false);}});
                        timer.setRepeats(false);
                        timer.start();
                    });
                }
            }

            if (Command.SET_AUCTION_RESULT.equals(command)) {
                if (response.payload() instanceof Map) {
                    Map<?, ?> responsePayload = (Map<?, ?>) response.payload();
                    if (responsePayload.get("auction") instanceof Auction) {
                        this_Auction = (Auction) responsePayload.get("auction");
                        syncAuctionSnapshot(this_Auction);
                    }
                    Platform.runLater(() -> {
                        if (this_Auction != null) {
                            updateBidChart(this_Auction.getBidHistory());
                        }
                    });
                }
            }
            if (Command.SET_ALLOW_RESULT.equals(command)) {
            j_notified.setText("Phiên đấu đã tạm dừng");
            j_status.setText("CHƯA PHÊ DUYỆT");
        }
        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            j_notified.setText("Sản phẩm đã bị xóa");
            j_status.setText("KHÔNG TỒN TẠI");
        }
        if(Command.NOTIFICATION.equals(command)){
            handleIncomingToastNotification(response.payload());
        }
    }
    }
