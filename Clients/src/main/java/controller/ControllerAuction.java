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

    // Đã sửa: Không gán cứng ngay tại đây để tránh NPE khi session trống
    private User p1;
    private Item item1;

    static Auction this_Auction;
    private String watchToken;
    private boolean finishHandled;


    @FXML private TextField j_setPrice;
    @FXML private Button j_apply;
    @FXML private Label j_leadingBidder;
    @FXML private Label j_LabelName;
    @FXML private Label j_days;
    @FXML private Label j_description;
    @FXML private ImageView j_image;
    @FXML private ImageView j_img;
    @FXML private Label j_name;
    @FXML private Button j_return;
    @FXML private Label j_textSoDu;
    @FXML private Label j_CurrentPrice;
    @FXML private Label j_notified;
    @FXML private Label j_mins;
    @FXML private Label j_hours;
    @FXML private Label j_secs;
    @FXML private Label j_status;
    @FXML private HBox j_countdown;
    @FXML private LineChart<String, Number> bidLineChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    @FXML
    public void initialize() throws IOException {
        client.setListener(this);

        // Nạp dữ liệu tài khoản
        p1 = UserSession.getLoggedInUser();
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();
    // Kiểm tra xem có sẵn session sản phẩm không (Trường hợp đi từ Danh sách sản phẩm thông thường)
        item1 = ItemSession.getLoggedInItem();

        if (item1 != null) {
            // Nếu đi từ màn hình chính (đã có ItemSession), kích hoạt kết nối luôn
            showSessionProductAndLoadingAuctionState();
            client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());
            client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", item1.getDatabaseId()));
        } else {
            // Nếu đi từ Lịch sử (ItemSession null), thiết lập giao diện chờ cơ bản trước
            if (p1 != null) {
                j_LabelName.setText(p1.getName());
                DecimalFormat df = new DecimalFormat("#,###");
                j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
            }
            j_status.setText("Đang tải cấu hình phòng...");
            j_status.setTextFill(Color.web("#bdc3c7"));
            j_leadingBidder.setText("—");
            if (j_countdown != null) {
                j_countdown.setVisible(false);
            }
            j_apply.setDisable(true);
            j_notified.setVisible(false);
        }
    }

    private void showSessionProductAndLoadingAuctionState() {
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }
        if (item1 != null) {
            j_name.setText(item1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
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
                System.out.println("⚠ Cảnh báo: Không tìm thấy phiên đấu giá!");
                if (p1 != null) j_LabelName.setText(p1.getName());

                // ĐÃ SỬA: Kiểm tra an toàn cho item1 tránh gây crash giao diện tại đây
                if (item1 != null) {
                    j_name.setText(item1.getName());
                    j_description.setText(item1.getDescription() != null ? item1.getDescription() : "");
                    renderImage();
                }

                j_status.setText("CHƯA DIỄN RA");
                j_apply.setDisable(true);
                j_notified.setText("Sản phẩm này hiện chưa được mở bán.");
                j_notified.setVisible(true);
                updatePriceAndLeader();
                return;
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
        if (p1 != null) j_LabelName.setText(p1.getName());
        if (item1 != null) {
            j_name.setText(item1.getName());
            j_description.setText(item1.getDescription() != null ? item1.getDescription() : "");
            renderImage();
        }
        updatePriceAndLeader();
        startStatusEngine();
    }

    private void updatePriceAndLeader() {
        DecimalFormat df = new DecimalFormat("#,###");
        if (this_Auction != null && this_Auction.getItem() != null) {
            double highestPrice = this_Auction.getItem().getCurrentHighestPrice();
            j_CurrentPrice.setText(df.format(highestPrice) + " VNĐ");
            if (item1 != null) {
                item1.setCurrentHighestPrice(highestPrice);
            }
        } else if (item1 != null) {
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        }

        if (this_Auction == null) {
            j_leadingBidder.setText("—");
            return;
        }

        String leader = this_Auction.getLeadingBidder();
        if (leader == null || leader.trim().isEmpty() || leader.equalsIgnoreCase("null")) {
            j_leadingBidder.setText("Chưa có");
        } else {
            j_leadingBidder.setText(leader.replace("\"", ""));
        }
    }

    private void syncAuctionSnapshot(Auction auction) {
        if (auction == null || item1 == null) return;
        this_Auction = auction;
        Item auctionItem = auction.getItem();
        if (auctionItem == null) return;

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
        if (item1 != null && item1.getImg() != null && !item1.getImg().isEmpty()) {
            if (item1.getImg().startsWith("http")) {
                j_img.setImage(new Image(item1.getImg()));
            } else {
                String imgPath = "/controller/img/" + item1.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }

    @FXML
    void On_apply(ActionEvent event) throws IOException {
        if (item1 == null || p1 == null) return;
        j_notified.setVisible(false);
        String priceText = j_setPrice.getText();

        if (priceText == null || priceText.trim().isEmpty()) {
            j_notified.setText("Vui lòng nhập giá đặt!");
            j_notified.setVisible(true);
            return;
        }

        try {
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
            j_notified.setText("Giá đặt phải là số nguyên hợp lệ!");
            j_notified.setVisible(true);
        }
    }

    @FXML void On_MouseClickImg(MouseEvent event) {}

    @FXML
    void On_Return(ActionEvent event) throws IOException {
        cleanup();
        SceneHelper.changeScene(j_return, "/fxml/BidderView.fxml");
        ItemSession.cleanItemSession();
        if (p1 != null) {
            client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", ""));
        }
    }

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
    private javafx.scene.shape.Circle connectionStatus;

    @FXML
    private Label connectionText;

    private ConnectionStatusManager statusManager;

    public void cleanup() {
        if (watchToken != null) {
            auctionEngine.unwatch(watchToken);
            watchToken = null;
        }
    }

    private void startStatusEngine() {
        if (item1 == null) return;
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
        if (p1 != null && this_Auction != null && this_Auction.getLeadingBidder() != null
                && this_Auction.getLeadingBidder().equals(p1.getUsername())) {
            j_notified.setText("Chúc mừng! Bạn đã thắng. Đang chuyển đến trang thanh toán...");
            j_notified.setVisible(true);
            PauseTransition delay = new PauseTransition(Duration.seconds(3));
            delay.setOnFinished(e -> SceneHelper.changeScene(j_apply, "/fxml/PayingView.fxml"));
            delay.play();
        } else {
            j_notified.setText("Phiên đấu giá đã kết thúc.");
            j_notified.setVisible(true);
        }
    }

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
                });
                series.getData().add(data);
            }

            bidLineChart.getData().add(series);

            Platform.runLater(() -> {
                Node line = series.getNode().lookup(".chart-series-line");
                if (line != null) {
                    line.setStyle("-fx-stroke-width: 1.5px; -fx-stroke: #f39c12;");
                    line.setMouseTransparent(true);
                }
                for (Node n : bidLineChart.lookupAll(".chart-symbol")) {
                    n.toFront();
                }
            });
        });
    }

    private String formatTime(Instant instant) {
        return DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private void handleIncomingToastNotification(Object payload) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            Map<String, Object> notifData = (Map<String, Object>) payload;
            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            Item item = (Item) notifData.get("item");
            final double finalPrice = newPrice;

            Platform.runLater(() -> {
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300);
                customToast.setStyle("-fx-background-color: #FFFFFF;");

                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;");

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                VBox textContainer = new VBox();
                textContainer.setSpacing(4);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                textContainer.setPadding(new Insets(10, 10, 10, 15));
                HBox.setHgrow(textContainer, Priority.ALWAYS);

                Label titleLabel = new Label("SẢN PHẨM CÓ LƯỢT ĐẤU GIÁ MỚI!");
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #212121;");

                Label messageLabel = new Label("Sản phẩm " + (item != null ? item.getName() : "") + " : " + df.format(finalPrice) + " VNĐ");
                messageLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(200);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconBlock, textContainer);

                org.controlsfx.control.Notifications notificationBuilder = org.controlsfx.control.Notifications.create()
                        .owner(j_textSoDu)
                        .graphic(customToast)
                        .hideAfter(Duration.seconds(4))
                        .position(Pos.BOTTOM_RIGHT);

                notificationBuilder.show();
            });
        } catch (Exception e) {
            System.err.println("Lỗi Toast Notification: " + e.getMessage());
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
                } else {
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

            Platform.runLater(() -> {
                Object auctionObj = update.get("auction");
                if (auctionObj instanceof Auction) {
                    this_Auction = (Auction) auctionObj;
                    syncAuctionSnapshot(this_Auction);
                }

                Object newPriceObj = update.get("newPrice");
                if (newPriceObj instanceof Number) {
                    item1.setCurrentHighestPrice(((Number) newPriceObj).doubleValue());
                }

                syncAuctionEndTime(update.get("auctionEndTime"));

                String bidderId = String.valueOf(update.get("bidderId"));
                if (this_Auction != null && bidderId != null && !bidderId.isEmpty() && !bidderId.equals("null")) {
                    this_Auction.setLeadingBidder(bidderId);
                }

                if (this_Auction != null) {
                    try {
                        updateBidChart(this_Auction.getBidHistory());
                        String newBidder = this_Auction.getLeadingBidder();

                        if (p1 != null && !p1.getUsername().equals(newBidder)) {
                            j_notified.setText("Có lượt đặt giá mới trong phiên.");
                            j_notified.setVisible(true);

                            PauseTransition hideDelay = new PauseTransition(Duration.seconds(4));
                            hideDelay.setOnFinished(e -> j_notified.setVisible(false));
                            hideDelay.play();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                updatePriceAndLeader();
                if (p1 != null) {
                    j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
                }
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

                    PauseTransition hideDelay = new PauseTransition(Duration.seconds(4));
                    hideDelay.setOnFinished(e -> j_notified.setVisible(false));
                    hideDelay.play();
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
            Platform.runLater(() -> {
                j_notified.setText("Phiên đấu đã tạm dừng");
                j_status.setText("CHƯA PHÊ DUYỆT");
            });
        }

        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            Platform.runLater(() -> {
                j_notified.setText("Sản phẩm đã bị xóa");
                j_status.setText("KHÔNG TỒN TẠI");
            });
        }

        if (Command.NOTIFICATION.equals(command)) {
            handleIncomingToastNotification(response.payload());
        }
    }

    // =========================================================================
    // KHÔNG CÒN LỖI: Phương thức tiếp nhận dữ liệu chuyển hướng an toàn từ Lịch sử
    // =========================================================================
    public void initData(model.auction.BidHistoryDTO dto) {
        if (dto == null) return;

        // Tái tạo p1 và gán nóng đối tượng item1 cục bộ
        this.p1 = UserSession.getLoggedInUser();
        this.item1 = new model.Items.Item();

        this.item1.setDatabaseId((int) dto.getItemId());
        this.item1.setName(dto.getItemName());
        this.item1.setCurrentHighestPrice(dto.getCurrentHighestPrice());

        // Đồng bộ dữ liệu mới tạo này vào Session của hệ thống
        model.Items.ItemSession.setLoggedInItem(this.item1);

        // Hiển thị nhanh dữ liệu tĩnh lên màn hình
        Platform.runLater(() -> {
            j_name.setText(dto.getItemName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(dto.getCurrentHighestPrice()) + " VNĐ");
            j_status.setText("Đang kết nối Server...");
        });

        try {
            // Đăng ký Listener và kích hoạt đẩy lệnh Socket đồng bộ lên Server
            client.setListener(this);
            client.sendCommand(Command.GET_AUCTION, dto.getItemId());
            if (p1 != null) {
                client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", dto.getItemId()));
            }
        } catch (IOException e) {
            System.err.println("Lỗi đồng bộ phòng đấu giá từ lịch sử: " + e.getMessage());
        }
    }
}