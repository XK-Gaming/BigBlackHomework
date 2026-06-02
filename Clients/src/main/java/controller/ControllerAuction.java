package controller;

import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
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
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.Items.ItemType;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import model.auction.BidHistoryDTO;

public class ControllerAuction implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();

    // Đã sửa: Không gán cứng ngay tại đây để tránh NPE khi session trống
    private User p1;
    private Item item1;

    static Auction this_Auction;
    private String watchToken;
    private boolean finishHandled;
    private PauseTransition finishRedirectDelay;

    // AutoBid UI state: lưu cấu hình/toggle theo user-item trong lúc client còn mở.
    private static final Map<String, AutoBidSettings> AUTO_BID_SETTINGS_BY_KEY = new HashMap<>();
    private static final Set<String> AUTO_BID_ENABLED_KEYS = new HashSet<>();
    private AutoBidSettings autoBidSettings;
    private boolean updatingAutoBidToggle;
    private Double pendingManualBidAmount;
    private Double pendingManualBidPreviousPrice;
    private boolean pendingManualBidWasLeading;

    @FXML private TextField j_setPrice;
    @FXML private Button j_apply;

    // AutoBid controls: setting mở popup nhập thông số, toggle gửi bật/tắt lên server.
    @FXML private Button j_autoBidSettings;
    @FXML private ToggleButton j_autoBidToggle;
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
    @FXML private Label j_MinBid;
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
        client.addListener(this);

        // Nạp dữ liệu tài khoản
        p1 = UserSession.getLoggedInUser();
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        // Kiểm tra xem có sẵn session sản phẩm không (Trường hợp đi từ Danh sách sản phẩm thông thường)
        item1 = ItemSession.getLoggedInItem();

        if (item1 != null) {
            // Nếu đi từ màn hình chính (đã có ItemSession), kích hoạt kết nối luôn
            showSessionProductAndLoadingAuctionState();
            restoreAutoBidState();
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
            setAutoBidAvailable(false);
            setAutoBidToggleSelected(false);
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
            j_description.setText(getCustomDescription(item1));
            renderImage();
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
            updateMinBidLabel();
        }
        j_status.setText("Đang tải phiên đấu giá...");
        j_status.setTextFill(Color.web("#bdc3c7"));
        j_leadingBidder.setText("—");
        if (j_countdown != null) {
            j_countdown.setVisible(false);
        }
        j_apply.setDisable(true);
        setAutoBidAvailable(false);
        setAutoBidToggleSelected(false);
        j_notified.setVisible(false);
    }

    public void onAuctionDataLoaded(Auction auction) {
        System.out.println("[DEBUG] Server trả về phiên đấu giá: " + auction);
        this_Auction = auction;
        Platform.runLater(() -> {
            if (this_Auction == null) {
                System.out.println("⚠ Cảnh báo: Không tìm thấy phiên đấu giá!");
                if (p1 != null) j_LabelName.setText(p1.getName());

                // ĐÃ SỬA: Kiểm tra an toàn cho item1 tránh gây crash giao diện tại đây
                if (item1 != null) {
                    j_name.setText(item1.getName());
                    j_description.setText(getCustomDescription(item1));
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

        // Hiển thị tên người dùng
        if (p1 != null) j_LabelName.setText(p1.getName());

        // 🔥 SỬA CHỖ NÀY: Kiểm tra an toàn trước khi set tên sản phẩm
        if (item1 != null) {
            // Chỉ cập nhật tên lên UI nếu tên trong item1 thực sự có chữ
            if (item1.getName() != null && !item1.getName().trim().isEmpty()) {
                j_name.setText(item1.getName());
            }

            j_description.setText(getCustomDescription(item1));
            renderImage();
        }

        updatePriceAndLeader();
        startStatusEngine();
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
            ItemType type = item.getRawItemType();

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
            updateMinBidLabel();
        }

        updateMinBidLabel();

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

    private void updateMinBidLabel() {
        if (j_MinBid == null) {
            return;
        }
        DecimalFormat df = new DecimalFormat("#,###");
        double minBid = item1 != null ? item1.getMinBid() : 0;
        j_MinBid.setText("MinBid: " + df.format(minBid) + " VNĐ");
    }

    private double minimumBidForCurrentState() {
        double currentPrice = item1 != null ? item1.getCurrentHighestPrice() : 0;
        if (isFirstBid()) {
            return Math.nextUp(currentPrice);
        }
        double minBid = item1 != null ? Math.max(0, item1.getMinBid()) : 0;
        return currentPrice + minBid;
    }

    private boolean isFirstBid() {
        if (this_Auction == null) {
            return false;
        }
        String leadingBidder = this_Auction.getLeadingBidder();
        boolean hasLeader = leadingBidder != null && !leadingBidder.isBlank() && !"null".equalsIgnoreCase(leadingBidder);
        boolean hasHistory = this_Auction.getBidHistory() != null && !this_Auction.getBidHistory().isEmpty();
        return !hasLeader && !hasHistory;
    }

    private String currentLeadingBidder() {
        if (this_Auction == null || this_Auction.getLeadingBidder() == null) {
            return "";
        }
        return this_Auction.getLeadingBidder().replace("\"", "").trim();
    }

    private void syncAuctionSnapshot(Auction auction) {
        if (auction == null || item1 == null) return;
        this_Auction = auction;
        Item auctionItem = auction.getItem();
        if (auctionItem == null) return;

        item1.setCurrentHighestPrice(auctionItem.getCurrentHighestPrice());
        item1.setMinBid(auctionItem.getMinBid());

        // 🔥 CHỈ ghi đè tên nếu dữ liệu từ Server trả về có tên hợp lệ
        if (auctionItem.getName() != null && !auctionItem.getName().trim().isEmpty()) {
            item1.setName(auctionItem.getName());
        }

        if (auctionItem.getAuctionStartTime() != null) {
            item1.setAuctionStartTime(auctionItem.getAuctionStartTime());
        }
        if (auctionItem.getAuctionEndTime() != null) {
            item1.setAuctionEndTime(auctionItem.getAuctionEndTime());
            finishHandled = false;
            cancelFinishRedirect();
        }
    }

    private void syncAuctionEndTime(Object endTimeValue) {
        if (item1 == null || !(endTimeValue instanceof Instant auctionEndTime)) {
            return;
        }
        item1.setAuctionEndTime(auctionEndTime);
        finishHandled = false;
        cancelFinishRedirect();
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
            double bidAmount = parseMoney(priceText, "Giá đặt");
            double minAllowedBid = minimumBidForCurrentState();
            double currentPrice = item1.getCurrentHighestPrice();

            if (!isFirstBid() && bidAmount < minAllowedBid) {
                DecimalFormat df = new DecimalFormat("#,###");
                j_notified.setText("Giá đặt tối thiểu là " + df.format(minAllowedBid) + " VNĐ (giá hiện tại + MinBid).");
                j_notified.setVisible(true);
            } else if (bidAmount <= currentPrice) {
                j_notified.setText("Giá đặt phải lớn hơn giá hiện tại!");
                j_notified.setVisible(true);
            } else if (bidAmount > p1.getBalance()) {
                j_notified.setText("Số dư không đủ để đấu giá");
                j_notified.setVisible(true);
            } else {
                pendingManualBidAmount = bidAmount;
                pendingManualBidPreviousPrice = currentPrice;
                pendingManualBidWasLeading = p1.getUsername().equals(currentLeadingBidder());
                client.sendCommand(Command.BID, Map.of(
                        "itemId", item1.getDatabaseId(),
                        "bidderId", p1.getUsername(),
                        "amount", bidAmount
                ));
            }
        } catch (IllegalArgumentException e) {
            j_notified.setText("Giá đặt phải là số hợp lệ!");
            j_notified.setVisible(true);
        }
    }

    @FXML
    void On_AutoBidSettings(ActionEvent event) {
        // AutoBid setting: mở popup để user nhập MaxBidAllow/BidGap và cập nhật lại server nếu toggle đang bật.
        boolean saved = showAutoBidSettingsDialog();
        if (saved && j_autoBidToggle != null && j_autoBidToggle.isSelected()) {
            sendAutoBidCommand(true);
        }
    }

    @FXML
    void On_AutoBidToggle(ActionEvent event) {
        // AutoBid toggle: bật thì yêu cầu có settings hợp lệ, tắt thì gửi lệnh dừng cho server.
        if (updatingAutoBidToggle || j_autoBidToggle == null) {
            return;
        }

        if (j_autoBidToggle.isSelected()) {
            if (autoBidSettings == null) {
                boolean saved = showAutoBidSettingsDialog();
                if (!saved) {
                    setAutoBidToggleSelected(false);
                    return;
                }
            }
            sendAutoBidCommand(true);
        } else {
            sendAutoBidCommand(false);
        }
    }

    @FXML void On_MouseClickImg(MouseEvent event) {}

    @FXML
    void On_Return(ActionEvent event) throws IOException {
        cleanup();
        client.removeListener(this);
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
        cancelFinishRedirect();
    }

    private void restoreAutoBidState() {
        String key = autoBidKey();
        autoBidSettings = AUTO_BID_SETTINGS_BY_KEY.get(key);
        setAutoBidToggleSelected(AUTO_BID_ENABLED_KEYS.contains(key));
        setAutoBidAvailable(false);
    }

    // AutoBid popup: dựng dialog cấu hình lớn hơn, bo góc và validate dữ liệu ngay trước khi lưu.
    private boolean showAutoBidSettingsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("AutoBid");
        dialog.setHeaderText(null);
        if (j_autoBidSettings != null && j_autoBidSettings.getScene() != null) {
            dialog.initOwner(j_autoBidSettings.getScene().getWindow());
        }

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(460);
        dialog.getDialogPane().setMinWidth(460);
        dialog.getDialogPane().setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: #dfe6e9;" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 14;");

        TextField maxBidField = new TextField(autoBidSettings != null ? plainNumber(autoBidSettings.maxBidAllow) : "");
        maxBidField.setPromptText("MaxBidAllow");
        styleAutoBidInput(maxBidField);
        TextField bidGapField = new TextField(autoBidSettings != null ? plainNumber(autoBidSettings.bidGap) : "");
        bidGapField.setPromptText("BidGap");
        styleAutoBidInput(bidGapField);
        Label validation = new Label();
        validation.setTextFill(Color.web("#e74c3c"));
        validation.setWrapText(true);
        validation.setMinHeight(22);

        Label title = new Label("Cài đặt AutoBid");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #243447;");
        Label subtitle = new Label("Nhập giới hạn giá tối đa và bước nhảy cho mỗi lượt đặt tự động.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-font-size: 13px; -fx-text-fill: #6c7a89;");

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.getColumnConstraints().addAll(labelColumn(), inputColumn());
        grid.add(styledFieldLabel("MaxBidAllow"), 0, 0);
        grid.add(maxBidField, 1, 0);
        grid.add(styledFieldLabel("BidGap"), 0, 1);
        grid.add(bidGapField, 1, 1);
        grid.add(validation, 0, 2, 2, 1);

        VBox content = new VBox(18, title, subtitle, grid);
        content.setPadding(new Insets(24, 26, 10, 26));
        content.setStyle("-fx-background-color: white; -fx-background-radius: 14;");
        dialog.getDialogPane().setContent(content);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        styleAutoBidDialogButton(saveButton, "#2ecc71", "white");
        styleAutoBidDialogButton(dialog.getDialogPane().lookupButton(ButtonType.CANCEL), "#ecf0f1", "#2c3e50");
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                parseAutoBidSettings(maxBidField.getText(), bidGapField.getText());
                validation.setText("");
            } catch (IllegalArgumentException ex) {
                validation.setText(ex.getMessage());
                event.consume();
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveButtonType) {
            autoBidSettings = parseAutoBidSettings(maxBidField.getText(), bidGapField.getText());
            AUTO_BID_SETTINGS_BY_KEY.put(autoBidKey(), autoBidSettings);
            showTemporaryNotice("AutoBid settings saved.");
            return true;
        }
        return false;
    }

    private Label styledFieldLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #5d6d7e;");
        return label;
    }

    private ColumnConstraints labelColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(118);
        column.setPrefWidth(126);
        return column;
    }

    private ColumnConstraints inputColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        column.setMinWidth(240);
        return column;
    }

    private void styleAutoBidInput(TextField field) {
        field.setPrefHeight(44);
        field.setMinHeight(44);
        field.setStyle(
                "-fx-font-size: 15px;" +
                "-fx-background-color: #f8fafc;" +
                "-fx-background-radius: 9;" +
                "-fx-border-color: #d8dee6;" +
                "-fx-border-radius: 9;" +
                "-fx-border-width: 1;" +
                "-fx-padding: 0 12 0 12;");
    }

    private void styleAutoBidDialogButton(Node button, String background, String textColor) {
        if (button == null) {
            return;
        }
        button.setStyle(
                "-fx-background-color: " + background + ";" +
                "-fx-text-fill: " + textColor + ";" +
                "-fx-background-radius: 8;" +
                "-fx-font-weight: bold;" +
                "-fx-padding: 8 18 8 18;");
        if (button instanceof Region region) {
            region.setMinHeight(38);
            region.setPrefHeight(38);
        }
    }

    private AutoBidSettings parseAutoBidSettings(String maxBidText, String bidGapText) {
        double maxBidAllow = parseMoney(maxBidText, "MaxBidAllow");
        double bidGap = parseMoney(bidGapText, "BidGap");
        double currentPrice = item1 != null ? item1.getCurrentHighestPrice() : 0;

        if (maxBidAllow <= currentPrice) {
            throw new IllegalArgumentException("MaxBidAllow phải lớn hơn giá hiện tại.");
        }
        if (bidGap <= 0) {
            throw new IllegalArgumentException("BidGap phải lớn hơn 0.");
        }
        double minBid = item1 != null ? item1.getMinBid() : 0;
        if (bidGap < minBid) {
            throw new IllegalArgumentException("BidGap phải lớn hơn hoặc bằng MinBid.");
        }
        return new AutoBidSettings(maxBidAllow, bidGap);
    }

    private double parseMoney(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        try {
            return Double.parseDouble(value.replace(",", "").replace(" ", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }

    // AutoBid command: đóng gói cấu hình hiện tại và gửi yêu cầu bật/tắt tới server.
    private void sendAutoBidCommand(boolean enabled) {
        if (p1 == null || item1 == null) {
            showTemporaryNotice("Cannot update AutoBid because session is missing.");
            setAutoBidToggleSelected(false);
            return;
        }

        if (enabled && autoBidSettings == null) {
            showTemporaryNotice("Please configure AutoBid first.");
            setAutoBidToggleSelected(false);
            return;
        }
        if (enabled && item1 != null && autoBidSettings.bidGap < item1.getMinBid()) {
            showTemporaryNotice("BidGap phải lớn hơn hoặc bằng MinBid.");
            setAutoBidToggleSelected(false);
            return;
        }

        try {
            if (enabled) {
                setAutoBidToggleSelected(true);
                client.sendCommand(Command.SET_AUTO_BID, Map.of(
                        "itemId", item1.getDatabaseId(),
                        "userId", p1.getUsername(),
                        "enabled", true,
                        "maxBidAllow", autoBidSettings.maxBidAllow,
                        "bidGap", autoBidSettings.bidGap
                ));
            } else {
                setAutoBidToggleSelected(false);
                AUTO_BID_ENABLED_KEYS.remove(autoBidKey());
                client.sendCommand(Command.SET_AUTO_BID, Map.of(
                        "itemId", item1.getDatabaseId(),
                        "userId", p1.getUsername(),
                        "enabled", false
                ));
            }
        } catch (IOException e) {
            showTemporaryNotice("Cannot send AutoBid request: " + e.getMessage());
            setAutoBidToggleSelected(false);
        }
    }

    // AutoBid result: đồng bộ toggle UI theo phản hồi server hoặc khi server tự tắt AutoBid.
    private void handleAutoBidResult(Object payload) {
        if (!(payload instanceof Map<?, ?> result)) {
            return;
        }

        String itemId = String.valueOf(result.get("itemId"));
        if (item1 != null && !String.valueOf(item1.getDatabaseId()).equals(itemId)) {
            return;
        }

        boolean success = booleanValue(result.get("success"));
        boolean enabled = booleanValue(result.get("enabled"));
        String message = result.get("message") != null ? String.valueOf(result.get("message")) : "";

        Platform.runLater(() -> {
            if (success) {
                syncLoggedInUserFromResponse(result);
            }
            if (enabled) {
                AUTO_BID_ENABLED_KEYS.add(autoBidKey());
            } else {
                AUTO_BID_ENABLED_KEYS.remove(autoBidKey());
            }
            setAutoBidToggleSelected(enabled);
            if (!success && message.isBlank()) {
                showTemporaryNotice("AutoBid failed.");
            } else if (!message.isBlank()) {
                showTemporaryNotice(message);
            }
        });
    }

    private boolean booleanValue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private void syncLoggedInUserFromResponse(Map<?, ?> result) {
        if (result == null) {
            return;
        }

        boolean appliedAuthoritativeBalance = UserBalanceSync.applyBalancePayload(result);
        p1 = UserSession.getLoggedInUser();

        if (!appliedAuthoritativeBalance) {
            applyManualBidBalanceFallback(result);
        }

        updateBalanceLabel();
    }

    private void syncLoggedInUserFromNotification(Map<?, ?> result) {
        if (result == null) {
            return;
        }

        if (UserBalanceSync.applyBalancePayload(result)) {
            p1 = UserSession.getLoggedInUser();
            updateBalanceLabel();
        }
    }

    private void applyManualBidBalanceFallback(Map<?, ?> result) {
        User currentUser = p1 != null ? p1 : UserSession.getLoggedInUser();
        if (currentUser == null || pendingManualBidAmount == null) {
            return;
        }

        Double bidAmount = numericValue(result.get("newPrice"));
        if (bidAmount == null) {
            bidAmount = pendingManualBidAmount;
        }

        double refund = pendingManualBidWasLeading && pendingManualBidPreviousPrice != null
                ? pendingManualBidPreviousPrice
                : 0;
        currentUser.setBalance(currentUser.getBalance() + refund - bidAmount);
        p1 = currentUser;
        UserSession.setLoggedInUser(currentUser);
    }

    private Double numericValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void updateBalanceLabel() {
        p1 = UserSession.getLoggedInUser();
        UserBalanceSync.refreshBalanceLabel(j_textSoDu);
    }

    // AutoBid availability: chỉ cho bật/tắt khi phiên đang RUNNING.
    private void clearPendingManualBid() {
        pendingManualBidAmount = null;
        pendingManualBidPreviousPrice = null;
        pendingManualBidWasLeading = false;
    }

    private void setAutoBidAvailable(boolean available) {
        if (j_autoBidToggle != null) {
            j_autoBidToggle.setDisable(!available);
        }
        if (j_autoBidSettings != null) {
            j_autoBidSettings.setDisable(false);
        }
    }

    private void setAutoBidToggleSelected(boolean selected) {
        if (j_autoBidToggle == null) {
            return;
        }
        updatingAutoBidToggle = true;
        j_autoBidToggle.setSelected(selected);
        j_autoBidToggle.setText(selected ? "AUTOBID ON" : "AUTOBID OFF");
        j_autoBidToggle.setStyle(selected
                ? "-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;"
                : "-fx-background-color: #ecf0f1; -fx-text-fill: #2c3e50; -fx-background-radius: 5; -fx-font-weight: bold;");
        updatingAutoBidToggle = false;
    }

    private String autoBidKey() {
        String username = p1 != null ? p1.getUsername() : "";
        String itemId = item1 != null ? String.valueOf(item1.getDatabaseId()) : "";
        return username + ":" + itemId;
    }

    private String plainNumber(double value) {
        return String.format("%.0f", value);
    }

    private void showTemporaryNotice(String message) {
        if (j_notified == null) {
            return;
        }
        j_notified.setText(message);
        j_notified.setVisible(true);
        PauseTransition hideDelay = new PauseTransition(Duration.seconds(4));
        hideDelay.setOnFinished(e -> j_notified.setVisible(false));
        hideDelay.play();
    }

    private void startStatusEngine() {
        if (item1 == null) return;
        cleanup();
        if (!hasAuctionSchedule()) {
            j_status.setText("Đang tải phiên đấu giá...");
            j_status.setTextFill(Color.web("#bdc3c7"));
            updateTimerLabels(0);
            j_apply.setDisable(true);
            setAutoBidAvailable(false);
            return;
        }
        finishHandled = false;
        watchToken = String.valueOf(auctionEngine.watchItem(item1, (status, secondsToNextChange) -> Platform.runLater(() -> {
            if (this_Auction != null) {
                this_Auction.setStatus(status);
            }
            updateTimerLabels(secondsToNextChange);
            switch (status) {
                case OPEN -> {
                    j_status.setText("CHƯA DIỄN RA");
                    j_apply.setDisable(true);
                    setAutoBidAvailable(false);
                }
                case RUNNING -> {
                    cancelFinishRedirect();
                    j_status.setText("ĐANG DIỄN RA");
                    j_apply.setDisable(false);
                    setAutoBidAvailable(true);
                }
                case FINISHED -> {
                    j_status.setText("ĐÃ KẾT THÚC");
                    j_apply.setDisable(true);
                    setAutoBidAvailable(false);
                    if (!finishHandled) {
                        finishHandled = true;
                        handleFinishedAuction();
                    }
                }
                case PAID, CANCELLED -> {
                    j_apply.setDisable(true);
                    setAutoBidAvailable(false);
                    j_status.setText(status == AuctionStatus.PAID ? "ĐÃ THANH TOÁN" : "ĐÃ HỦY");
                }
            }
        })));
    }

    private javafx.stage.Stage findValidStage() {
        // Thử lấy stage từ các node hiện tại
        if (j_return != null && j_return.getScene() != null) return (javafx.stage.Stage) j_return.getScene().getWindow();
        if (j_apply != null && j_apply.getScene() != null) return (javafx.stage.Stage) j_apply.getScene().getWindow();

        // Nếu chịu chết, lấy Stage đầu tiên đang hiển thị của ứng dụng
        return javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof javafx.stage.Stage && w.isShowing())
                .map(w -> (javafx.stage.Stage) w)
                .findFirst()
                .orElse(null);
    }

    private void handleFinishedAuction() {
        if (!isAuctionReallyFinished()) {
            finishHandled = false;
            return;
        }

        if (p1 != null && this_Auction != null && this_Auction.getLeadingBidder() != null
                && this_Auction.getLeadingBidder().equals(p1.getUsername())) {

            j_notified.setText("Chúc mừng! Bạn đã thắng. Đang chuyển đến trang thanh toán...");
            j_notified.setVisible(true);

            finishRedirectDelay = new PauseTransition(Duration.seconds(3));
            finishRedirectDelay.setOnFinished(e -> {
                finishRedirectDelay = null;
                if (!isAuctionReallyFinished()) {
                    finishHandled = false;
                    return;
                }
                javafx.stage.Stage stage = findValidStage();
                if (stage != null) {
                    client.removeListener(this);
                    SceneHelper.changeScene(stage, "/fxml/PayingView.fxml");
                } else {
                    System.err.println("[CRITICAL] Không tìm thấy Stage nào đang mở để chuyển trang!");
                }
            });
            finishRedirectDelay.play();
        } else {
            j_notified.setText("Phiên đấu giá đã kết thúc.");
            j_notified.setVisible(true);

            finishRedirectDelay = new PauseTransition(Duration.seconds(3));
            finishRedirectDelay.setOnFinished(e -> {
                finishRedirectDelay = null;
                if (!isAuctionReallyFinished()) {
                    finishHandled = false;
                    return;
                }
                javafx.stage.Stage stage = findValidStage();
                if (stage != null) {
                    client.removeListener(this);
                    SceneHelper.changeScene(stage, "/fxml/BidderView.fxml");
                }
            });
            finishRedirectDelay.play();
        }
    }

    private boolean hasAuctionSchedule() {
        return item1 != null
                && item1.getAuctionStartTime() != null
                && item1.getAuctionEndTime() != null;
    }

    private boolean isAuctionReallyFinished() {
        return item1 != null
                && item1.getAuctionEndTime() != null
                && !Instant.now().isBefore(item1.getAuctionEndTime());
    }

    private void cancelFinishRedirect() {
        if (finishRedirectDelay != null) {
            finishRedirectDelay.stop();
            finishRedirectDelay = null;
        }
    }

    private void updateBidChart(List<BidTransaction> historyList) {
        Platform.runLater(() -> {
            bidLineChart.getData().clear();
            if (historyList == null || historyList.isEmpty()) {
                bidLineChart.setTitle("Chưa có lượt đấu giá nào");
                return;
            }
             bidLineChart.setTitle("Lịch sử đấu giá (" + historyList.size() + " lượt)");
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
        if (response == null || response.command() == null) return;

        DecimalFormat df = new DecimalFormat("#,###");
        Command command = response.command();

        // 1. Xử lý tải dữ liệu phiên đấu giá ban đầu
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            // Đã sửa: Ép kiểu an toàn bằng cách kiểm tra instanceof trước để tránh crash app
            if (response.payload() instanceof Auction) {
                this_Auction = (Auction) response.payload();
                onAuctionDataLoaded(this_Auction);
                syncAuctionSnapshot(this_Auction);

                Platform.runLater(() -> {
                    if (this_Auction != null) {
                        updateBidChart(this_Auction.getBidHistory());
                    }
                });
            } else {
                // Payload trả về null hoặc báo lỗi
                Platform.runLater(() -> {
                    j_notified.setText("Phiên đấu giá không tồn tại hoặc đã bị xóa.");
                    j_notified.setVisible(true);
                    j_status.setText("KHÔNG TỒN TẠI");
                    j_status.setVisible(true);
                });
            }
        }

        // 2. KÍCH HOẠT REAL-TIME: Nhận cập nhật trạng thái tự động từ AuctionEngine gửi về
        if (Command.UPDATE_AUCTION_STATUS.equals(command)) {
            if (response.payload() instanceof Map) {
                Map<?, ?> updateData = (Map<?, ?>) response.payload();
                String itemIdStr = String.valueOf(updateData.get("itemId"));
                String newStatusStr = String.valueOf(updateData.get("newStatus"));

                // Kiểm tra xem có đúng là sản phẩm người dùng hiện tại đang đứng xem không
                if (item1 != null && String.valueOf(item1.getDatabaseId()).equals(itemIdStr)) {
                    Platform.runLater(() -> {
                        try {
                            AuctionStatus newStatus = AuctionStatus.valueOf(newStatusStr);
                            if (this_Auction != null) {
                                this_Auction.setStatus(newStatus);
                            }

                            j_status.setText(newStatusStr);

                            // Nếu phiên kết thúc tự động hoặc bị sếp đóng ngầm
                            if (newStatus == AuctionStatus.FINISHED ) {
                                j_status.setTextFill(Color.web("#e74c3c")); // Đổi chữ sang màu đỏ
                                j_apply.setDisable(true);                  // Khóa cứng nút đặt giá
                                setAutoBidToggleSelected(false);          // Tắt tự động đặt giá
                                j_notified.setText("Phiên đấu giá đã khép lại theo thời gian quy định.");
                                j_notified.setVisible(true);
                            } else if (newStatus == AuctionStatus.RUNNING) {
                                j_status.setTextFill(Color.web("#2ecc71")); // Đổi màu xanh lá
                                j_apply.setDisable(false);                 // Kích hoạt mở nút
                            }
                        } catch (Exception e) {
                            System.err.println("Lỗi đồng bộ trạng thái Real-time: " + e.getMessage());
                        }
                    });
                }
            }
        }

        // 3. Có người khác trong phòng vừa đặt giá thành công
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

        // 4. Kết quả đặt giá của CHÍNH BẢN THÂN gửi lên
        if (Command.BID_RESULT.equals(command)) {
            if (response.payload() instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) response.payload();
                boolean isSuccess = (boolean) result.get("success");
                String message = (String) result.get("message");
                Platform.runLater(() -> {
                    if (isSuccess) {
                        syncLoggedInUserFromResponse(result);
                        clearPendingManualBid();
                        syncAuctionEndTime(result.get("auctionEndTime"));
                        j_notified.setText("Đấu giá thành công");
                    } else {
                        clearPendingManualBid();
                        j_notified.setText(message);
                    }
                    j_notified.setVisible(true);

                    PauseTransition hideDelay = new PauseTransition(Duration.seconds(4));
                    hideDelay.setOnFinished(e -> j_notified.setVisible(false));
                    hideDelay.play();
                });
            }
        }

        // 5. Kết quả thiết lập phòng đấu giá
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

        // 6. Admin hoặc Seller tạm dừng phiên đấu giá giữa chừng
        if (Command.SET_ALLOW_RESULT.equals(command)) {
            Platform.runLater(() -> {
                if (j_notified == null || j_notified.getScene() == null) return;

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Thông báo");
                alert.setHeaderText("Phiên đấu giá bị tạm dừng!");
                alert.setContentText("Hệ thống sẽ tự động đưa bạn về màn hình chính sau 4 giây...");
                alert.show();

                Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(4), event -> {
                    if (alert.isShowing()) {
                        alert.close();
                    }
                    if (j_notified != null && j_notified.getScene() != null) {
                        client.removeListener(this);
                        SceneHelper.changeScene(j_notified, "/fxml/BidderView.fxml");
                    }
                }));
                timeline.play();
            });
        }

        // 7. Kết quả cài đặt AutoBid
        if (Command.SET_AUTO_BID_RESULT.equals(command)) {
            handleAutoBidResult(response.payload());
        }

        // 8. ĐÃ SỬA: Gom gộp duy nhất một khối xử lý Xóa sản phẩm chuẩn xác theo cấu trúc Map dữ liệu
        if (Command.DELETE_ITEM_RESULT.equals(command)) {
            if (response.payload() instanceof Map) {
                Map<String, Object> result = (Map<String, Object>) response.payload();
                boolean isSuccess = (boolean) result.getOrDefault("success", false);
                String message = (String) result.getOrDefault("message", "Sản phẩm này đã bị xóa khỏi hệ thống.");

                Platform.runLater(() -> {
                    Alert alert = new Alert(isSuccess ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                    alert.setTitle("Thông báo");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    alert.showAndWait();

                    if (j_notified != null && j_notified.getScene() != null) {
                        client.removeListener(this);
                        SceneHelper.changeScene(j_notified, "/fxml/BidderView.fxml");
                    }
                });
            }
        }

        // 9. Nhận thông báo chung hoặc thông báo Toast biến động số dư nạp tiền
        if (Command.NOTIFICATION.equals(command)) {
            if (response.payload() instanceof Map<?, ?> notificationPayload) {
                Platform.runLater(() -> syncLoggedInUserFromNotification(notificationPayload));
            }
            handleIncomingToastNotification(response.payload());
        }

        // 10. Biến động số dư tài khoản trực tiếp
        if (Command.BALANCE_UPDATE.equals(command)) {
            Platform.runLater(() -> {
                if (UserBalanceSync.applyBalancePayload(response.payload())) {
                    p1 = UserSession.getLoggedInUser();
                    updateBalanceLabel();
                }
            });
        }

        // 11. Tài khoản bị cưỡng chế đăng xuất (Do Admin xử lý danh sách đen)
        if (Command.FORCE_LOGOUT.equals(command)) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Tài khoản bị xóa");
                alert.setHeaderText(null);
                alert.setContentText("Tài khoản của bạn đã bị Admin xóa khỏi hệ thống. Ứng dụng sẽ tự động đóng.");
                alert.showAndWait();
                System.exit(0);
            });
        }
    }

    // =========================================================================
    // KHÔNG CÒN LỖI: Phương thức tiếp nhận dữ liệu chuyển hướng an toàn từ Lịch sử
    // =========================================================================
    public void initData(model.auction.BidHistoryDTO dto) {
        if (dto == null) return;

        this.p1 = UserSession.getLoggedInUser();
        this.item1 = new model.Items.Item();

        this.item1.setDatabaseId((int) dto.getItemId());
        this.item1.setName(dto.getItemName());
        this.item1.setCurrentHighestPrice(dto.getCurrentHighestPrice());

        // Tạm thời giả định MinBid để tránh lỗi chia/cộng trừ khi chưa có dữ liệu server
        this.item1.setMinBid(10000);

        model.Items.ItemSession.setLoggedInItem(this.item1);
        restoreAutoBidState();

        Platform.runLater(() -> {
            j_name.setText(dto.getItemName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(dto.getCurrentHighestPrice()) + " VNĐ");
            j_status.setText("Đang kết nối Server...");

            // Cập nhật các label cơ bản khác tránh bị trống rỗng text
            j_description.setText(getCustomDescription(item1));
            updateMinBidLabel();
        });

        try {
            client.addListener(this);
            client.sendCommand(Command.GET_AUCTION, dto.getItemId());
            if (p1 != null) {
                client.sendCommand(Command.SET_AUCTION, Map.of("userId", p1.getUsername(), "itemId", dto.getItemId()));
            }

        } catch (IOException e) {
            System.err.println("Lỗi đồng bộ phòng đấu giá từ lịch sử: " + e.getMessage());
        }
    }

    // AutoBid settings: object nhỏ giữ hai thông số user nhập trong popup.
    private static final class AutoBidSettings {
        private final double maxBidAllow;
        private final double bidGap;

        private AutoBidSettings(double maxBidAllow, double bidGap) {
            this.maxBidAllow = maxBidAllow;
            this.bidGap = bidGap;
        }
    }
}
