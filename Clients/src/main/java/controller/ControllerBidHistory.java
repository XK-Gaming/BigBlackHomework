package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

// Lịch sử bid.
public class ControllerBidHistory implements ServerListener {

    private final AuctionClient client = AuctionClient.getInstance();
    private String currentUsername = "";

    @FXML private ScrollPane scrollPane;
    @FXML private Button btnBack;

    @FXML private AnchorPane paneModalOverlay;
    @FXML private Label lblModalItemName;
    @FXML private Label lblModalCurrentPrice;
    @FXML private Label lblModalMinBid;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnConfirmBid;

    @FXML private javafx.scene.shape.Circle connectionStatus;
    @FXML private Label connectionText;
    private ConnectionStatusManager statusManager;

    private VBox containerCardList;
    private long targetItemId = -1;

    private List<BidHistoryDTO> allHistoryData = new ArrayList<>();

    // Khởi tạo màn hình.
    @FXML
    public void initialize() {

        client.addListener(this);
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
    // Yêu cầu dữ liệu server.
    private void requestDataFromServer() {
        if (currentUsername != null && !currentUsername.isBlank()) {
            try {
                client.sendCommand(Command.GET_BIDDER_HISTORY, currentUsername);
            } catch (IOException e) {
                System.err.println("Lỗi yêu cầu lịch sử: " + e.getMessage());
            }
        }
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();
        System.out.println("[Client Debug] Nhận phản hồi tại Lịch sử, Command: " + command);

        // Nhận lịch sử bidder.
        if (Command.GET_BIDDER_HISTORY_RESULT.equals(command)) {
            @SuppressWarnings("unchecked")
            List<BidHistoryDTO> historyList = (List<BidHistoryDTO>) response.payload();
            this.allHistoryData = historyList != null ? historyList : new ArrayList<>();
            Platform.runLater(() -> updateUIWithData(this.allHistoryData));
            return;
        }

        // Nhận trạng thái phiên realtime.
        if (Command.UPDATE_AUCTION_STATUS.equals(command)) {
            if (response.payload() instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();
                        long targetItemId = itemIdStr.contains(".") ? Double.valueOf(itemIdStr).longValue() : Long.parseLong(itemIdStr);
                        String newStatusStr = map.get("newStatus") != null ? map.get("newStatus").toString() : "";

                        Platform.runLater(() -> {
                            System.out.println("[Client History Realtime] Item " + targetItemId + " đổi trạng thái sang: " + newStatusStr);

                            boolean isUpdated = false;

                            if ("FINISHED".equalsIgnoreCase(newStatusStr)) {

                                new Thread(this::requestDataFromServer).start();
                                return;
                            }
                            for (BidHistoryDTO dto : allHistoryData) {
                                if (dto.getItemId() == targetItemId) {

                                    if ("RUNNING".equalsIgnoreCase(newStatusStr)) {
                                        if (dto.getMyHighestBid() >= dto.getCurrentHighestPrice()) {
                                            dto.setStatus("WINNING");
                                        } else {
                                            dto.setStatus("OUTBID");
                                        }
                                    } else {

                                        dto.setStatus(newStatusStr);
                                    }
                                    isUpdated = true;
                                }
                            }

                            if (isUpdated) {
                                updateUIWithData(this.allHistoryData);
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Không thể bóc tách trạng thái tại Lịch sử: " + e.getMessage());
                }
                return;
            }
        }

        // Nhận bid realtime.
        if (Command.BID_UPDATE.equals(command) || Command.ITEMS_UPDATE.equals(command) || Command.BID_RESULT.equals(command)) {
            System.out.println("[Client History DEBUG] BID_RESULT payload: " + response.payload());
            Object payload = response.payload();
            if (payload == null) return;

            long updatedItemId = -1;
            double updatedPrice = 0.0;
            double updatedMinBid = -1.0;
            String highestBidder = "";

            if (payload instanceof Auction auction) {
                updatedItemId = auction.getItem() != null ? auction.getItem().getDatabaseId() : -1;
                updatedPrice = auction.getCurrentPrice();
                updatedMinBid = auction.getItem() != null ? auction.getItem().getMinBid() : -1.0;
                highestBidder = auction.getLeadingBidder() != null ? auction.getLeadingBidder() : "";
            }

            else if (payload instanceof Item item) {
                updatedItemId = item.getDatabaseId();
                updatedPrice = item.getCurrentHighestPrice();
                updatedMinBid = item.getMinBid();
            }

            else if (payload instanceof Map<?, ?> map) {

                // Nhận kết quả bid.
                if (Command.BID_RESULT.equals(command)) {
                    Object successObj = map.get("success");
                    Object messageObj = map.get("message");
                    if (successObj != null && "false".equalsIgnoreCase(successObj.toString()) && messageObj != null) {
                        String msg = messageObj.toString();
                        java.util.regex.Pattern p = java.util.regex.Pattern.compile("([0-9][0-9.,]+)");
                        java.util.regex.Matcher m = p.matcher(msg);
                        if (m.find() && targetItemId != -1) {
                            String numStr = m.group(1);
                            String digits = numStr.replaceAll("[^0-9]", "");
                            if (!digits.isEmpty()) {
                                double minAllowed = Double.parseDouble(digits);

                                final double finalMinAllowed = Math.ceil(minAllowed);
                                final String textVal = String.format(Locale.ROOT, "%.0f", finalMinAllowed);
                                Platform.runLater(() -> {
                                    if (txtBidAmount != null) txtBidAmount.setText(textVal);

                                    if (targetItemId != -1) {
                                        for (BidHistoryDTO dto : allHistoryData) {
                                            if (dto.getItemId() == targetItemId) {

                                                double derived = finalMinAllowed - dto.getCurrentHighestPrice();
                                                double derivedMin = derived > 0 ? derived : 0;
                                                dto.setMinBid(derivedMin);

                                                if (lblModalMinBid != null) {
                                                    DecimalFormat df = new DecimalFormat("#,###");
                                                    lblModalMinBid.setText(df.format(derivedMin) + " VNĐ");
                                                }
                                                System.out.println("[Client History] Updated DTO minBid to: " + derivedMin + " for item " + targetItemId);
                                                break;
                                            }
                                        }
                                    }
                                    System.out.println("[Client History] Server requested minAllowed, adjusted input to: " + textVal);
                                });
                            }
                        }
                    }
                }

                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();
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

                        if (map.containsKey("minBid") && map.get("minBid") != null) {
                            try {
                                updatedMinBid = Double.parseDouble(map.get("minBid").toString());
                            } catch (Exception ignored) { updatedMinBid = -1.0; }
                        } else if (map.containsKey("min_bid") && map.get("min_bid") != null) {
                            try {
                                updatedMinBid = Double.parseDouble(map.get("min_bid").toString());
                            } catch (Exception ignored) { updatedMinBid = -1.0; }
                        }

                        if (map.get("bidderId") != null) {
                            highestBidder = map.get("bidderId").toString();
                        } else if (map.get("username") != null) {
                            highestBidder = map.get("username").toString();
                        }

                        String updatedItemName = "";
                        if (map.get("name") != null) {
                            updatedItemName = map.get("name").toString();
                        } else if (map.get("itemName") != null) {
                            updatedItemName = map.get("itemName").toString();
                        }

                        if (updatedItemId > 0) {
                            final long targetId = updatedItemId;
                            final double targetPrice = updatedPrice;
                            final double targetMinBid = updatedMinBid;
                            final String topBidder = highestBidder;
                            final String finalItemName = updatedItemName;

                            Platform.runLater(() -> {
                                updateSingleHistoryItem(targetId, targetPrice, topBidder.isEmpty() ? currentUsername : topBidder, finalItemName, targetMinBid);

                                // Nhận kết quả bid.
                                if (Command.BID_RESULT.equals(command)) {
                                    hideQuickBidModal();
                                    System.out.println("[Client History] Đã cập nhật xong kết quả BID_RESULT cho Item: " + targetId);
                                }
                            });
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Không thể bóc tách payload Map lịch sử: " + e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }

            if (updatedItemId > 0) {
                final long targetId = updatedItemId;
                final double targetPrice = updatedPrice;
                final double targetMinBid = updatedMinBid;
                final String topBidder = highestBidder;

                String tempName = "";
                if (payload instanceof Auction auction && auction.getItem() != null) {
                    tempName = auction.getItem().getName();
                } else if (payload instanceof Item item) {
                    tempName = item.getName();
                }
                final String finalItemName = tempName;

                Platform.runLater(() -> {
                    updateSingleHistoryItem(targetId, targetPrice, topBidder.isEmpty() ? currentUsername : topBidder, finalItemName, targetMinBid);

                    // Nhận kết quả bid.
                    if (Command.BID_RESULT.equals(command)) {
                        hideQuickBidModal();
                        System.out.println("[Client History] Đã cập nhật xong kết quả BID_RESULT cho Item: " + targetId);
                    }
                });
            }
        }

        // Nhận lệnh đăng xuất cưỡng chế.
        if (Command.FORCE_LOGOUT.equals(command)) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Tài khoản bị xóa");
                alert.setHeaderText(null);
                alert.setContentText("Tài khoản của bạn đã bị Admin xóa. Ứng dụng sẽ tự đóng.");
                alert.showAndWait();
                System.exit(0);
            });
        }
    }
    // Cập nhật một lịch sử bid.
    private void updateSingleHistoryItem(long itemId, double newPrice, String topBidder, String newItemName, double newMinBid) {
        boolean hasChanged = false;

        String currentTimeStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now());

        String safeCurrentUsername = (currentUsername != null) ? currentUsername.trim() : "";

        for (BidHistoryDTO dto : allHistoryData) {
            if (dto.getItemId() == itemId) {

                if (newItemName != null && !newItemName.isBlank()) {
                    dto.setItemName(newItemName);
                }

                if (newMinBid >= 0) {
                    dto.setMinBid(newMinBid);
                }

                dto.setCurrentHighestPrice(newPrice);
                dto.setLastBidTime(currentTimeStr);

                String safeTopBidder = (topBidder != null) ? topBidder.trim() : "";

                if (!safeTopBidder.isEmpty()) {
                    if (safeTopBidder.equalsIgnoreCase(safeCurrentUsername)) {
                        dto.setStatus("WINNING");
                        if (newPrice > dto.getMyHighestBid()) {
                            dto.setMyHighestBid(newPrice);
                        }
                    } else {
                        if (newPrice >= dto.getMyHighestBid()) {
                            dto.setStatus("OUTBID");
                        }
                    }
                } else {
                    if (dto.getMyHighestBid() >= newPrice) {
                        dto.setStatus("WINNING");
                    } else {
                        dto.setStatus("OUTBID");
                    }
                }

                hasChanged = true;
                System.out.println("🔄 [UI Realtime] Đã đồng bộ Item ID: " + itemId
                        + " | Tên: " + dto.getItemName()
                        + " | Giá mới: $" + newPrice
                        + " | MinBid: " + dto.getMinBid()
                        + " | Trạng thái mới: " + dto.getStatus());
            }
        }

        if (hasChanged) {
            updateUIWithData(allHistoryData);
        }
    }
    // Vẽ lịch sử bid.
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
    // Dựng card lịch sử.
    private HBox createSmartCard(BidHistoryDTO dto) {
        HBox card = new HBox(20);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15, 20, 15, 20));

        String baseStyle = "-fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 10, 0, 0, 4); ";
        String statusColorStyle = "";
        String statusText = "";
        boolean showRebidButton = false;
        boolean showPaymentButton = false;

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
                showPaymentButton = true;
            }
            case "LOST" -> {
                statusColorStyle = "-fx-background-color: #F5F5F5; -fx-border-color: #BDBDBD; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "⚪ Đã kết thúc";
            }
        }
        card.setStyle(baseStyle + statusColorStyle);

        VBox txtSection = new VBox(6);

        String rawName = dto.getItemName();
        String finalDisplayName;

        if (rawName != null && !rawName.isBlank() && !rawName.startsWith("Sản phẩm mã #")) {
            finalDisplayName = rawName + " (Mã #" + dto.getItemId() + ")";
        } else {
            finalDisplayName = "Sản phẩm mã #" + dto.getItemId();
        }

        Label nameLabel = new Label(finalDisplayName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

        nameLabel.setWrapText(false);
        nameLabel.setMaxWidth(280);

        Label timeLabel = new Label("Lượt đặt cuối: " + dto.getLastBidTime());
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #616161;");
        txtSection.getChildren().addAll(nameLabel, timeLabel);
        HBox.setHgrow(txtSection, Priority.ALWAYS);

        VBox priceSection = new VBox(4);
        priceSection.setAlignment(Pos.CENTER_RIGHT);
        double myHighestBid = dto.getMyHighestBid();
        double currentHighestPrice = dto.getCurrentHighestPrice();

        Label myBidLabel = new Label(String.format("Mức đặt của bạn: %,.0f VNĐ", myHighestBid));
        myBidLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #424242;");

        Label currentMaxLabel = new Label(String.format("Giá hiện tại: %,.0f VNĐ", currentHighestPrice));
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

        if (showPaymentButton) {
            Button btnPayment = new Button("Vào thanh toán");

            btnPayment.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
            btnPayment.setPadding(new Insets(5, 10, 5, 10));

            btnPayment.setOnAction(e -> {
                client.removeListener(this);

                var targetController = SceneHelper.changeSceneAndGetController(btnPayment, "/fxml/PayingView.fxml");
                if (targetController instanceof ControllerPayment paymentCtrl) {
                    paymentCtrl.initData(dto);
                    System.out.println("[Chuyển hướng] Đã kích hoạt initData cho ControllerPayment thành công.");
                }
            });
            actionSection.getChildren().add(btnPayment);
        }

        Button btnGoToAuction = new Button("Vào phòng ĐG");
        btnGoToAuction.setStyle("-fx-background-color: #1976D2; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
        btnGoToAuction.setPadding(new Insets(5, 10, 5, 10));

        btnGoToAuction.setOnAction(e -> {
            client.removeListener(this);
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
    // Popup bid nhanh.
    private void showQuickBidModal(BidHistoryDTO dto) {
        System.out.println("[Debug Modal] Hiển thị modal cho Item ID: " + dto.getItemId());

        this.targetItemId = dto.getItemId();
        lblModalItemName.setText(dto.getItemName());
        lblModalCurrentPrice.setText( dto.getCurrentHighestPrice()+" VNĐ");

        double minBid = 10.0;
        try {
            minBid = dto.getMinBid();
        } catch (Exception ignored) {}
        double suggestedPrice = dto.getCurrentHighestPrice() + (minBid > 0 ? minBid : 10.0);

        txtBidAmount.clear();

        txtBidAmount.setText(String.format(Locale.ROOT, "%.0f", suggestedPrice));
        txtBidAmount.requestFocus();
        txtBidAmount.selectAll();

        try {
            if (lblModalMinBid != null) {
                DecimalFormat df = new DecimalFormat("#,###");
                lblModalMinBid.setText(df.format(minBid) + " VNĐ");
            }
        } catch (Exception ignored) {}

        paneModalOverlay.setDisable(false);
        paneModalOverlay.setVisible(true);
        paneModalOverlay.toFront();

        System.out.println("[Debug Modal] Modal hiển thị thành công, targetItemId = " + this.targetItemId);
    }

    // Xử lý thao tác.
    @FXML
    private void handleCloseModal(ActionEvent event) {
        hideQuickBidModal();
    }
    // Ẩn popup bid nhanh.
    private void hideQuickBidModal() {
        paneModalOverlay.setVisible(false);
        paneModalOverlay.setDisable(true);
        txtBidAmount.clear();
        targetItemId = -1;
        System.out.println("[Debug Modal] Modal đã đóng");
    }

    // Gửi bid nhanh.
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

            String sanitized = amountText.replaceAll("[^0-9]", "");
            if (sanitized.isEmpty()) {
                System.err.println("[Lỗi Nhập Liệu] Mức giá nhập vào không đúng định dạng số! Input: " + amountText);
                return;
            }
            double amount = Double.parseDouble(sanitized);

            System.out.println("[Debug Bid] Chuẩn bị BID: itemId=" + targetItemId + ", amount=" + amount + ", bidder=" + currentUsername);

            BidHistoryDTO targetDto = null;
            for (BidHistoryDTO dto : allHistoryData) {
                if (dto.getItemId() == targetItemId) { targetDto = dto; break; }
            }
            double minAllowed = Double.NEGATIVE_INFINITY;
            if (targetDto != null) {
                double minBid = targetDto.getMinBid();
                if (minBid <= 0) minBid = 10.0;
                minAllowed = targetDto.getCurrentHighestPrice() + minBid;

                minAllowed = Math.ceil(minAllowed);
                if (amount < minAllowed) {
                    System.err.println("[Client Validation] Giá quá thấp. Mức đặt tối thiểu là " + String.format("%,.0f", minAllowed) + " VNĐ (hiện tại + MinBid). Đã điều chỉnh ô nhập.");
                txtBidAmount.setText(String.format(Locale.ROOT, "%.0f", minAllowed));
                    return;
                }
            }

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

    // Quay lại màn trước.
    @FXML
    private void handleBackAction(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(btnBack, "/fxml/BidderView.fxml");
    }
}
