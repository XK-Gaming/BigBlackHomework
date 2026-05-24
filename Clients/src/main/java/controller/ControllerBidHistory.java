package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.User.User;
import model.User.UserSession;
import model.auction.BidHistoryDTO;
import model.auction.Auction;
import model.Items.Item;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerBidHistory implements ServerListener {

    private final AuctionClient client = AuctionClient.getInstance();
    private String currentUsername = "";

    @FXML private ScrollPane scrollPane;
    @FXML private Button btnBack;

    @FXML private AnchorPane paneModalOverlay;
    @FXML private Label lblModalItemName;
    @FXML private Label lblModalCurrentPrice;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnConfirmBid;

    @FXML private javafx.scene.shape.Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    private VBox containerCardList;
    private long targetItemId = -1;

    // Lưu danh sách lịch sử cục bộ để cập nhật realtime
    private List<BidHistoryDTO> allHistoryData = new ArrayList<>();

    @FXML
    public void initialize() {
        // Đảm bảo đăng ký listener với client khi vào màn hình này
        client.setListener(this);
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        containerCardList = new VBox(15);
        containerCardList.setPadding(new Insets(15));
        scrollPane.setContent(containerCardList);
        scrollPane.setFitToWidth(true);

        Label lblLoading = new Label("Đang tải lịch sử đặt giá từ hệ thống...");
        lblLoading.setStyle("-fx-font-size: 14px; -fx-text-fill: gray;");
        containerCardList.getChildren().add(lblLoading);

        User currentUser = UserSession.getLoggedInUser();
        if (currentUser != null) {
            this.currentUsername = currentUser.getUsername();
        }

        if (paneModalOverlay != null) {
            paneModalOverlay.setVisible(false);
            paneModalOverlay.setDisable(true);
        }

        Platform.runLater(this::requestDataFromServer);
    }

    private void requestDataFromServer() {
        if (currentUsername != null && !currentUsername.isBlank()) {
            try {
                client.sendCommand(Command.GET_BIDDER_HISTORY, currentUsername);
            } catch (IOException e) {
                System.err.println("Lỗi yêu cầu lịch sử: " + e.getMessage());
            }
        }
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();
        System.out.println("[Client Debug] Nhận phản hồi tại Lịch sử, Command: " + command);

        // TRƯỜNG HỢP 1: Tải toàn bộ danh sách lịch sử ban đầu từ Server
        if (Command.GET_BIDDER_HISTORY_RESULT.equals(command)) {
            @SuppressWarnings("unchecked")
            List<BidHistoryDTO> historyList = (List<BidHistoryDTO>) response.payload();
            this.allHistoryData = historyList != null ? historyList : new ArrayList<>();
            Platform.runLater(() -> updateUIWithData(this.allHistoryData));
            return;
        }

        // TRƯỜNG HỢP 2: Cập nhật biến động Realtime từng Item từ sảnh hoặc kết quả Bid cá nhân
        if (Command.BID_UPDATE.equals(command) || Command.ITEMS_UPDATE.equals(command) || Command.BID_RESULT.equals(command)) {
            Object payload = response.payload();
            if (payload == null) return;

            long updatedItemId = -1;
            double updatedPrice = 0.0;
            String highestBidder = "";

            // 1. Nhận dạng cấu trúc phòng đấu giá (ITEMS_UPDATE được broadcast toàn hệ thống)
            if (payload instanceof Auction auction) {
                updatedItemId = auction.getItem() != null ? auction.getItem().getDatabaseId() : -1;
                updatedPrice = auction.getCurrentPrice();
                highestBidder = auction.getLeadingBidder() != null ? auction.getLeadingBidder() : "";
            }
            // 2. Nhận dạng Object sản phẩm đơn lẻ (Item)
            else if (payload instanceof Item item) {
                updatedItemId = item.getDatabaseId();
                updatedPrice = item.getCurrentHighestPrice();
            }
            // 3. ✅ SỬA ĐỔI: Gia cố bóc tách ép kiểu an toàn từ cấu trúc Map chuỗi/số
            else if (payload instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();
                        // Chống lỗi nếu itemId trả về là dạng số thực "12.0" hoặc chuỗi thuần "12"
                        if (itemIdStr.contains(".")) {
                            updatedItemId = Double.valueOf(itemIdStr).longValue();
                        } else {
                            updatedItemId = Long.parseLong(itemIdStr);
                        }

                        if (map.containsKey("newPrice") && map.get("newPrice") != null) {
                            updatedPrice = Double.parseDouble(map.get("newPrice").toString());
                        } else if (map.containsKey("amount") && map.get("amount") != null) {
                            updatedPrice = Double.parseDouble(map.get("amount").toString());
                        }

                        if (map.get("bidderId") != null) {
                            highestBidder = map.get("bidderId").toString();
                        } else if (map.get("username") != null) {
                            highestBidder = map.get("username").toString();
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Không thể bóc tách payload Map lịch sử: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }

            // Tiến hành cập nhật luồng UI an toàn
            if (updatedItemId > 0) {
                final long targetId = updatedItemId;
                final double targetPrice = updatedPrice;
                final String topBidder = highestBidder;

                // ✅ SỬA ĐỔI: Chạy toàn bộ khối logic tính toán dữ liệu cục bộ vào luồng JavaFX
                Platform.runLater(() -> {
                    updateSingleHistoryItem(targetId, targetPrice, topBidder.isEmpty() ? currentUsername : topBidder);

                    if (Command.BID_RESULT.equals(command)) {
                        hideQuickBidModal();
                        System.out.println("[Client History] Đã cập nhật xong kết quả BID_RESULT cho Item: " + targetId);
                    }
                });
            }
        }
    }

    /**
     * XỬ LÝ REALTIME: Tìm kiếm item thay đổi và cập nhật trực tiếp lên giao diện
     */
    /**
     * XỬ LÝ REALTIME: Tìm kiếm item thay đổi và cập nhật trực tiếp cả GIÁ và TRẠNG THÁI
     */
    private void updateSingleHistoryItem(long itemId, double newPrice, String topBidder) {
        boolean hasChanged = false;

        String currentTimeStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now());

        // Chuẩn hóa tên người dùng hiện tại để so sánh chính xác
        String safeCurrentUsername = (currentUsername != null) ? currentUsername.trim() : "";

        for (BidHistoryDTO dto : allHistoryData) {
            if (dto.getItemId() == itemId) {

                // 1. Cập nhật lại giá cao nhất trên thị trường hiện tại
                dto.setCurrentHighestPrice(newPrice);
                dto.setLastBidTime(currentTimeStr);

                // Chuẩn hóa tên người người vừa đặt giá cao nhất từ server gửi về
                String safeTopBidder = (topBidder != null) ? topBidder.trim() : "";

                // 2. BIỆN PHÁP ĐỊNH ĐOẠT TRẠNG THÁI CHÍNH XÁC
                if (!safeTopBidder.isEmpty()) {
                    // TRƯỜNG HỢP A: Nếu chính bạn là người vừa dẫn đầu (vừa bid thành công hoặc hệ thống ghi nhận bạn giữ top)
                    if (safeTopBidder.equalsIgnoreCase(safeCurrentUsername)) {
                        dto.setStatus("WINNING");

                        // Cập nhật lại mức giá cao nhất của riêng bạn cho khớp với thị trường
                        if (newPrice > dto.getMyHighestBid()) {
                            dto.setMyHighestBid(newPrice);
                        }
                    }
                    // TRƯỜNG HỢP B: Nếu người dẫn đầu là người khác
                    else {
                        // Nếu giá thị trường đã vượt qua mức trả cao nhất của bạn -> Chắc chắn bị vượt mặt
                        if (newPrice > dto.getMyHighestBid()) {
                            dto.setStatus("OUTBID");
                        }
                        // Dự phòng: Nếu giá bằng nhau nhưng người giữ top không phải mình -> Vẫn tính là bị vượt mặt
                        else if (newPrice == dto.getMyHighestBid()) {
                            dto.setStatus("OUTBID");
                        }
                    }
                } else {
                    // TRƯỜNG HỢP C: Server không trả về tên người giữ top (Cơ chế tính toán dự phòng dựa trên giá tiền)
                    if (dto.getMyHighestBid() >= newPrice) {
                        dto.setStatus("WINNING");
                    } else {
                        dto.setStatus("OUTBID");
                    }
                }

                hasChanged = true;
                System.out.println("🔄 [UI Realtime] Đã đồng bộ Item ID: " + itemId
                        + " | Giá mới: $" + newPrice
                        + " | Người giữ top: " + (safeTopBidder.isEmpty() ? "Ẩn danh" : safeTopBidder)
                        + " | Trạng thái mới: " + dto.getStatus());
            }
        }

        // Vẽ lại toàn bộ danh sách card lên giao diện JavaFX ngay khi trạng thái đổi
        if (hasChanged) {
            updateUIWithData(allHistoryData);
        }
    }

    private void updateUIWithData(List<BidHistoryDTO> historyList) {
        containerCardList.getChildren().clear();

        if (historyList == null || historyList.isEmpty()) {
            Label lblEmpty = new Label("Bạn chưa tham gia đặt giá ở sản phẩm nào.");
            lblEmpty.setStyle("-fx-font-size: 14px; -fx-text-fill: #757575; -fx-font-style: italic;");
            containerCardList.getChildren().add(lblEmpty);
            return;
        }

        for (BidHistoryDTO dto : historyList) {
            HBox smartCard = createSmartCard(dto);
            containerCardList.getChildren().add(smartCard);
        }

        containerCardList.requestLayout();
        scrollPane.requestLayout();
    }

    private HBox createSmartCard(BidHistoryDTO dto) {
        HBox card = new HBox(20);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15, 20, 15, 20));

        String baseStyle = "-fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4); ";
        String statusColorStyle = "";
        String statusText = "";
        boolean showRebidButton = false;

        switch (dto.getStatus()) {
            case "WINNING" -> {
                statusColorStyle = "-fx-background-color: #E8F5E9; -fx-border-color: #4CAF50; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "🟢 Đang dẫn đầu";
            }
            case "OUTBID" -> {
                statusColorStyle = "-fx-background-color: #FFEBEE; -fx-border-color: #F44336; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "🔴 Bị vượt mặt!";
                showRebidButton = true;
            }
            case "WON" -> {
                statusColorStyle = "-fx-background-color: #FFFDE7; -fx-border-color: #FFC107; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "🏆 Thắng cuộc";
            }
            case "LOST" -> {
                statusColorStyle = "-fx-background-color: #F5F5F5; -fx-border-color: #BDBDBD; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "⚪ Đã kết thúc";
            }
        }
        card.setStyle(baseStyle + statusColorStyle);

        VBox txtSection = new VBox(6);
        Label nameLabel = new Label(dto.getItemName());
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #212121;");
        Label timeLabel = new Label("Lượt đặt cuối: " + dto.getLastBidTime());
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #616161;");
        txtSection.getChildren().addAll(nameLabel, timeLabel);
        HBox.setHgrow(txtSection, Priority.ALWAYS);

        VBox priceSection = new VBox(4);
        priceSection.setAlignment(Pos.CENTER_RIGHT);
        Label myBidLabel = new Label("Mức đặt của bạn: $" + dto.getMyHighestBid());
        myBidLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #424242;");
        Label currentMaxLabel = new Label("Giá hiện tại: $" + dto.getCurrentHighestPrice());
        currentMaxLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #E65100;");
        priceSection.getChildren().addAll(myBidLabel, currentMaxLabel);

        VBox actionSection = new VBox(8);
        actionSection.setAlignment(Pos.CENTER);
        Label statusLabel = new Label(statusText);
        statusLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        actionSection.getChildren().add(statusLabel);

        if (showRebidButton) {
            Button rebidBtn = new Button("Re-bid nhanh");
            rebidBtn.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
            rebidBtn.setPadding(new Insets(5, 10, 5, 10));
            rebidBtn.setOnAction(e -> showQuickBidModal(dto));
            actionSection.getChildren().add(rebidBtn);
        }

        Button btnGoToAuction = new Button("Vào phòng ĐG");
        btnGoToAuction.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
        btnGoToAuction.setPadding(new Insets(5, 10, 5, 10));

        btnGoToAuction.setOnAction(e -> {
            client.setListener(null);
            ControllerAuction targetController = SceneHelper.changeSceneAndGetController(btnGoToAuction, "/fxml/BiddingView.fxml");
            if (targetController != null) {
                targetController.initData(dto);
                System.out.println("[Chuyển hướng] Đã chuyển tới phòng đấu giá cho Item ID: " + dto.getItemId());
            }
        });
        actionSection.getChildren().add(btnGoToAuction);

        card.getChildren().addAll(txtSection, priceSection, actionSection);
        return card;
    }

    private void showQuickBidModal(BidHistoryDTO dto) {
        System.out.println("[Debug Modal] Hiển thị modal cho Item ID: " + dto.getItemId());

        this.targetItemId = dto.getItemId();
        lblModalItemName.setText(dto.getItemName());
        lblModalCurrentPrice.setText("$" + dto.getCurrentHighestPrice());

        double suggestedPrice = dto.getCurrentHighestPrice() + 10.0;

        txtBidAmount.clear();
        txtBidAmount.setText(String.valueOf(suggestedPrice));
        txtBidAmount.requestFocus();
        txtBidAmount.selectAll();

        paneModalOverlay.setDisable(false);
        paneModalOverlay.setVisible(true);
        paneModalOverlay.toFront();

        System.out.println("[Debug Modal] Modal hiển thị thành công, targetItemId = " + this.targetItemId);
    }

    @FXML
    private void handleCloseModal(ActionEvent event) {
        hideQuickBidModal();
    }

    private void hideQuickBidModal() {
        paneModalOverlay.setVisible(false);
        paneModalOverlay.setDisable(true);
        txtBidAmount.clear();
        targetItemId = -1;
        System.out.println("[Debug Modal] Modal đã đóng");
    }

    @FXML
    private void handleConfirmQuickBid(ActionEvent event) {
        System.out.println("[Debug Bid] handleConfirmQuickBid được gọi!");

        String amountText = txtBidAmount.getText().trim();

        if (targetItemId == -1) {
            System.err.println("[Lỗi] Không xác định được sản phẩm cần Re-bid! targetItemId = " + targetItemId);
            return;
        }

        if (amountText.isEmpty()) {
            System.err.println("[Lỗi] Số tiền đặt giá không được để trống!");
            return;
        }

        try {
            double amount = Double.parseDouble(amountText);

            System.out.println("[Debug Bid] Chuẩn bị BID: itemId=" + targetItemId + ", amount=" + amount + ", bidder=" + currentUsername);

            Map<String, Object> bidPayload = new HashMap<>();
            bidPayload.put("itemId", targetItemId);
            bidPayload.put("bidderId", currentUsername);
            bidPayload.put("amount", amount);

            System.out.println("[Client History] Đang chuẩn bị gửi lệnh BID: " + bidPayload);

            new Thread(() -> {
                try {
                    client.sendCommand(Command.BID, bidPayload);
                    System.out.println("[Client History] Gửi lệnh BID thành công qua Socket.");
                } catch (IOException e) {
                    System.err.println("[Lỗi Mạng] Không thể gửi lệnh Re-bid: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

        } catch (NumberFormatException e) {
            System.err.println("[Lỗi Nhập Liệu] Mức giá nhập vào không đúng định dạng số thực! Input: " + amountText);
        }
    }

    @FXML
    private void handleBackAction(ActionEvent event) {
        client.setListener(null);
        SceneHelper.changeScene(btnBack, "/fxml/BidderView.fxml");
    }
}