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

    // Lưu danh sách lịch sử cục bộ để cập nhật realtime
    private List<BidHistoryDTO> allHistoryData = new ArrayList<>();

    @FXML
    public void initialize() {
        // Đảm bảo đăng ký listener với client khi vào màn hình này
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

        //  TRƯỜNG HỢP MỚI: Đón nhận sự thay đổi trạng thái tự động từ Engine quét định kỳ
        if (Command.UPDATE_AUCTION_STATUS.equals(command)) {
            if (response.payload() instanceof Map<?, ?> map) {
                try {
                    if (map.containsKey("itemId") && map.get("itemId") != null) {
                        String itemIdStr = map.get("itemId").toString();
                        long targetItemId = itemIdStr.contains(".") ? Double.valueOf(itemIdStr).longValue() : Long.parseLong(itemIdStr);
                        String newStatusStr = map.get("newStatus") != null ? map.get("newStatus").toString() : "";

                        Platform.runLater(() -> {
                            System.out.println("[Client History Realtime] Item " + targetItemId + " đổi trạng thái sang: " + newStatusStr);

                            // Cập nhật trạng thái mới trực tiếp vào danh sách dữ liệu trong bộ nhớ RAM của Client
                            boolean isUpdated = false;
                            // Nếu Server báo FINISHED — có thể DB chưa đồng bộ endTime khi anti-sniping kéo dài,
                            // nên yêu cầu tải lại toàn bộ lịch sử từ Server để có trạng thái chính xác.
                            if ("FINISHED".equalsIgnoreCase(newStatusStr)) {
                                // Tải lại dữ liệu lịch sử từ Server để tránh hiện trạng sai khi thời gian bị kéo dài
                                new Thread(this::requestDataFromServer).start();
                                return;
                            }
                            for (BidHistoryDTO dto : allHistoryData) {
                                if (dto.getItemId() == targetItemId) {
                                    // Server có thể gửi trạng thái RUNNING; chuyển sang WINNING/OUTBID cho hiển thị lịch sử
                                    if ("RUNNING".equalsIgnoreCase(newStatusStr)) {
                                        if (dto.getMyHighestBid() >= dto.getCurrentHighestPrice()) {
                                            dto.setStatus("WINNING");
                                        } else {
                                            dto.setStatus("OUTBID");
                                        }
                                    } else {
                                        // Fallback: giữ nguyên mapping nếu Server gửi WON/LOST/WINNING/OUTBID
                                        dto.setStatus(newStatusStr);
                                    }
                                    isUpdated = true;
                                }
                            }

                            // Nếu tìm thấy item trong list lịch sử hiện tại, vẽ lại giao diện Table/List để đồng bộ chữ ngay
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

        // TRƯỜNG HỢP 3: Cập nhật biến động Realtime từng Item từ sảnh hoặc kết quả Bid cá nhân
        if (Command.BID_UPDATE.equals(command) || Command.ITEMS_UPDATE.equals(command) || Command.BID_RESULT.equals(command)) {
            System.out.println("[Client History DEBUG] BID_RESULT payload: " + response.payload());
            Object payload = response.payload();
            if (payload == null) return;

            long updatedItemId = -1;
            double updatedPrice = 0.0;
            double updatedMinBid = -1.0;
            String highestBidder = "";

            // 1. Nhận dạng cấu trúc phòng đấu giá (ITEMS_UPDATE được broadcast toàn hệ thống)
            if (payload instanceof Auction auction) {
                updatedItemId = auction.getItem() != null ? auction.getItem().getDatabaseId() : -1;
                updatedPrice = auction.getCurrentPrice();
                updatedMinBid = auction.getItem() != null ? auction.getItem().getMinBid() : -1.0;
                highestBidder = auction.getLeadingBidder() != null ? auction.getLeadingBidder() : "";
            }
            // 2. Nhận dạng Object sản phẩm đơn lẻ (Item)
            else if (payload instanceof Item item) {
                updatedItemId = item.getDatabaseId();
                updatedPrice = item.getCurrentHighestPrice();
                updatedMinBid = item.getMinBid();
            }
            // 3. Khối bóc tách ép kiểu an toàn từ cấu trúc Map chuỗi/số
            else if (payload instanceof Map<?, ?> map) {
                // Special-case: BID_RESULT failure may return a formatted message like
                // "Giá đặt tối thiểu là 1.641.000 (giá hiện tại + MinBid)." without itemId.
                // Try to extract suggested minimum and update modal input so user can retry.
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
                                // Update input and also update local DTO minBid so future validation uses server value
                                final double finalMinAllowed = Math.ceil(minAllowed);
                                final String textVal = String.format(Locale.ROOT, "%.0f", finalMinAllowed);
                                Platform.runLater(() -> {
                                    if (txtBidAmount != null) txtBidAmount.setText(textVal);
                                    // Update DTO in-memory minBid for targetItemId
                                    if (targetItemId != -1) {
                                        for (BidHistoryDTO dto : allHistoryData) {
                                            if (dto.getItemId() == targetItemId) {
                                                // Server gave absolute minimum required (minAllowed). To ensure suggestion equals server's requirement,
                                                // set currentHighestPrice to minAllowed and clear minBid locally — this makes suggestedPrice == minAllowed.
                                                double derived = finalMinAllowed - dto.getCurrentHighestPrice();
                                                double derivedMin = derived > 0 ? derived : 0;
                                                dto.setMinBid(derivedMin);
                                                // Update modal minBid label if modal is visible
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

                        // Thử lấy minBid nếu server gửi kèm trong payload Map
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

                        // BÓC TÁCH TÊN SẢN PHẨM TỪ MAP REALTIME
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

            // Tiến hành cập nhật luồng UI an toàn cho trường hợp Object là Auction hoặc Item
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

                    if (Command.BID_RESULT.equals(command)) {
                        hideQuickBidModal();
                        System.out.println("[Client History] Đã cập nhật xong kết quả BID_RESULT cho Item: " + targetId);
                    }
                });
            }
        }

        // TRƯỜNG HỢP 4: Tài khoản bị cưỡng chế đăng xuất do Admin xử lý danh sách đen
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

    /**
     * XỬ LÝ REALTIME: Tìm kiếm item thay đổi và cập nhật trực tiếp lên giao diện
     */
    /**
     * XỬ LÝ REALTIME: Tìm kiếm item thay đổi và cập nhật trực tiếp cả GIÁ và TRẠNG THÁI
     */
    private void updateSingleHistoryItem(long itemId, double newPrice, String topBidder, String newItemName, double newMinBid) {
        boolean hasChanged = false;

        String currentTimeStr = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.now());

        String safeCurrentUsername = (currentUsername != null) ? currentUsername.trim() : "";

        for (BidHistoryDTO dto : allHistoryData) {
            if (dto.getItemId() == itemId) {

                // --- THÊM: CẬP NHẬT LẠI TÊN SẢN PHẨM NẾU TRÊN SERVER CÓ THAY ĐỔI HOẶC BỊ KHUYẾT ---
                if (newItemName != null && !newItemName.isBlank()) {
                    dto.setItemName(newItemName);
                }

                // --- CẬP NHẬT minBid NẾU Server gửi kèm ---
                if (newMinBid >= 0) {
                    dto.setMinBid(newMinBid);
                }

                // 1. Cập nhật lại giá cao nhất trên thị trường hiện tại
                dto.setCurrentHighestPrice(newPrice);
                dto.setLastBidTime(currentTimeStr);

                // 2. BIỆN PHÁP ĐỊNH ĐOẠT TRẠNG THÁI CHÍNH XÁC
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
        boolean showPaymentButton = false; // Thêm biến cờ để kiểm soát nút thanh toán

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
                showPaymentButton = true; // Bật hiển thị nút thanh toán khi đấu giá thành công
            }
            case "LOST" -> {
                statusColorStyle = "-fx-background-color: #F5F5F5; -fx-border-color: #BDBDBD; -fx-border-radius: 10; -fx-border-width: 1.5;";
                statusText = "⚪ Đã kết thúc";
            }
        }
        card.setStyle(baseStyle + statusColorStyle);

        VBox txtSection = new VBox(6);

        // ==================== ĐOẠN ĐÃ ĐƯỢC NÂNG CẤP HIỂN THỊ TÊN ====================
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
        // ===========================================================================

        Label timeLabel = new Label("Lượt đặt cuối: " + dto.getLastBidTime());
        timeLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #616161;");
        txtSection.getChildren().addAll(nameLabel, timeLabel);
        HBox.setHgrow(txtSection, Priority.ALWAYS);

        VBox priceSection = new VBox(4);
        priceSection.setAlignment(Pos.CENTER_RIGHT);
        double myHighestBid = dto.getMyHighestBid();
        double currentHighestPrice = dto.getCurrentHighestPrice();

// Định dạng hiển thị chuỗi tiền tệ VNĐ (ví dụ: 10,000,000 VNĐ)
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

        // 1. NÚT RE-BID NHANH (KHI BỊ VƯỢT MẶT)
        if (showRebidButton) {
            Button rebidBtn = new Button("Re-bid nhanh");
            rebidBtn.setStyle("-fx-background-color: #D32F2F; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
            rebidBtn.setPadding(new Insets(5, 10, 5, 10));
            rebidBtn.setOnAction(e -> showQuickBidModal(dto));
            actionSection.getChildren().add(rebidBtn);
        }

        // 2. NÚT VÀO THANH TOÁN (KHI ĐẤU GIÁ THÀNH CÔNG - WON)
        if (showPaymentButton) {
            Button btnPayment = new Button("Vào thanh toán");
            // Giao diện màu Cam nổi bật phong cách Payment
            btnPayment.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;");
            btnPayment.setPadding(new Insets(5, 10, 5, 10));

            btnPayment.setOnAction(e -> {
                client.removeListener(this); // Gỡ bỏ listener hiện tại trước khi chuyển màn hình

                var targetController = SceneHelper.changeSceneAndGetController(btnPayment, "/fxml/PayingView.fxml");
                if (targetController instanceof ControllerPayment paymentCtrl) {
                    paymentCtrl.initData(dto); // Gọi hàm truyền dữ liệu vừa viết ở Bước 1
                    System.out.println("[Chuyển hướng] Đã kích hoạt initData cho ControllerPayment thành công.");
                }
            });
            actionSection.getChildren().add(btnPayment);
        }

        // 3. NÚT VÀO PHÒNG ĐẤU GIÁ (Nên ẩn đi hoặc giữ lại tùy bạn, ở đây giữ nguyên theo code cũ)
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

    private void showQuickBidModal(BidHistoryDTO dto) {
        System.out.println("[Debug Modal] Hiển thị modal cho Item ID: " + dto.getItemId());

        this.targetItemId = dto.getItemId();
        lblModalItemName.setText(dto.getItemName());
        lblModalCurrentPrice.setText("$" + dto.getCurrentHighestPrice());

        double minBid = 10.0;
        try {
            minBid = dto.getMinBid();
        } catch (Exception ignored) {}
        double suggestedPrice = dto.getCurrentHighestPrice() + (minBid > 0 ? minBid : 10.0);

        txtBidAmount.clear();
        // Use culture-neutral integer formatting (no grouping) to avoid locale grouping parsing issues
        txtBidAmount.setText(String.format(Locale.ROOT, "%.0f", suggestedPrice));
        txtBidAmount.requestFocus();
        txtBidAmount.selectAll();

        // Show MinBid explicitly on modal
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
            // Sanitize input: remove any non-digit characters (commas, dots, spaces) since VND uses whole numbers
            String sanitized = amountText.replaceAll("[^0-9]", "");
            if (sanitized.isEmpty()) {
                System.err.println("[Lỗi Nhập Liệu] Mức giá nhập vào không đúng định dạng số! Input: " + amountText);
                return;
            }
            double amount = Double.parseDouble(sanitized);

            System.out.println("[Debug Bid] Chuẩn bị BID: itemId=" + targetItemId + ", amount=" + amount + ", bidder=" + currentUsername);

            // Client-side validation: ensure amount >= currentHighestPrice + minBid
            BidHistoryDTO targetDto = null;
            for (BidHistoryDTO dto : allHistoryData) {
                if (dto.getItemId() == targetItemId) { targetDto = dto; break; }
            }
            double minAllowed = Double.NEGATIVE_INFINITY;
            if (targetDto != null) {
                double minBid = targetDto.getMinBid();
                if (minBid <= 0) minBid = 10.0;
                minAllowed = targetDto.getCurrentHighestPrice() + minBid;
                // Round up to whole number (no fractional VND)
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

    @FXML
    private void handleBackAction(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene(btnBack, "/fxml/BidderView.fxml");
    }
}