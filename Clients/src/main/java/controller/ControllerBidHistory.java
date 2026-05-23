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
import model.auction.Auction; // Import nếu cần thiết để ép kiểu payload
import model.Items.Item;      // Import nếu cần thiết để ép kiểu payload
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

    private VBox containerCardList;
    private long targetItemId = -1;

    // BỔ SUNG: Lưu danh sách lịch sử cục bộ để cập nhật realtime giống file ControllerBidder
    private List<BidHistoryDTO> allHistoryData = new ArrayList<>();

    @FXML
    public void initialize() {
        client.setListener(this);

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

        // TRƯỜNG HỢP 1: Tải toàn bộ danh sách lịch sử ban đầu
        if (Command.GET_BIDDER_HISTORY_RESULT.equals(command)) {
            @SuppressWarnings("unchecked")
            List<BidHistoryDTO> historyList = (List<BidHistoryDTO>) response.payload();
            this.allHistoryData = historyList != null ? historyList : new ArrayList<>();
            Platform.runLater(() -> updateUIWithData(this.allHistoryData));
            return; // Xử lý xong thì dừng lại luôn
        }

        // TRƯỜNG HỢP 2: Cập nhật Realtime từng item
        if (Command.BID_UPDATE.equals(command) || Command.ITEMS_UPDATE.equals(command) || Command.BID_RESULT.equals(command)) {

            Object payload = response.payload();
            if (payload == null) return;

            long updatedItemId = -1;
            double updatedPrice = 0.0;
            String highestBidder = "";

            // 1. Nếu payload là cấu trúc phòng đấu giá (Auction)
            if (payload instanceof Auction auction) {
                updatedItemId = auction.getItem() != null ? auction.getItem().getDatabaseId() : -1;
                updatedPrice = auction.getCurrentPrice();
                highestBidder = auction.getLeadingBidder() != null ? auction.getLeadingBidder() : "";
            }
            // 2. Nếu payload là Object sản phẩm đơn lẻ (Item)
            else if (payload instanceof Item item) {
                updatedItemId = item.getDatabaseId();
                updatedPrice = item.getCurrentHighestPrice();
            }
            // 3. Nếu payload là một Map (Cần kiểm tra null cực kỳ nghiêm ngặt ở đây)
            else if (payload instanceof Map<?, ?> map) {
                try {
                    // Kiểm tra an toàn trước khi ép kiểu, tránh lỗi .toString() trên object null
                    if (map.containsKey("itemId") && map.get("itemId") != null &&
                            map.containsKey("amount") && map.get("amount") != null) {

                        updatedItemId = Long.parseLong(map.get("itemId").toString());
                        updatedPrice = Double.parseDouble(map.get("amount").toString());

                        if (map.get("bidderId") != null) {
                            highestBidder = map.get("bidderId").toString();
                        }
                    } else {
                        // Nếu nhận được BID_RESULT nhưng map không chứa dữ liệu cần thiết,
                        // ta bỏ qua không xử lý để tránh ghi đè dữ liệu sai lệch ($0.0)
                        System.out.println("[Client History] Nhận Map thông báo kết quả phụ, bỏ qua cập nhật UI.");
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("Không thể bóc tách payload Map: " + e.getMessage());
                    return;
                }
            }

            // Chỉ tiến hành cập nhật giao diện nếu tìm thấy ID hợp lệ (> 0)
            if (updatedItemId > 0) {
                final long targetId = updatedItemId;
                final double targetPrice = updatedPrice;
                final String topBidder = highestBidder;

                Platform.runLater(() -> {
                    hideQuickBidModal();
                    updateSingleHistoryItem(targetId, targetPrice, topBidder);
                });
            }
        }
    }
    /**
     * XỬ LÝ REALTIME: Tìm kiếm item thay đổi và cập nhật trực tiếp lên giao diện
     */
    private void updateSingleHistoryItem(long itemId, double newPrice, String topBidder) {
        boolean hasChanged = false;

        for (BidHistoryDTO dto : allHistoryData) {
            if (dto.getItemId() == itemId) {
                // 1. Cập nhật lại giá cao nhất hiện tại của sản phẩm
                dto.setCurrentHighestPrice(newPrice);

                // 2. Cập nhật lại trạng thái dựa trên việc ai đang dẫn đầu
                if (!topBidder.isEmpty()) {
                    if (topBidder.equals(currentUsername)) {
                        dto.setStatus("WINNING");
                        // Nếu mình là người vừa bid thành công, mức đặt của mình chính là mức giá này
                        dto.setMyHighestBid(newPrice);
                    } else {
                        dto.setStatus("OUTBID");
                    }
                } else {
                    // Nếu server không trả về tên người dẫn đầu, kiểm tra dựa trên giá tiền
                    if (dto.getMyHighestBid() >= newPrice) {
                        dto.setStatus("WINNING");
                    } else {
                        dto.setStatus("OUTBID");
                    }
                }
                hasChanged = true;
                System.out.println("[UI Realtime Lịch Sử] Đã cập nhật Item ID: " + itemId + " thành giá mới: $" + newPrice);
            }
        }

        // Nếu có sự thay đổi, vẽ lại danh sách card ngay lập tức (Rất nhẹ và mượt vì không gọi mạng)
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
            containerCardList.getChildren().add(createSmartCard(dto));
        }
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

        card.getChildren().addAll(txtSection, priceSection, actionSection);
        return card;
    }

    private void showQuickBidModal(BidHistoryDTO dto) {
        this.targetItemId = dto.getItemId();
        lblModalItemName.setText(dto.getItemName());
        lblModalCurrentPrice.setText("$" + dto.getCurrentHighestPrice());

        double suggestedPrice = dto.getCurrentHighestPrice() + 10.0;
        txtBidAmount.setText(String.valueOf(suggestedPrice));

        paneModalOverlay.setVisible(true);
    }

    @FXML
    private void handleCloseModal(ActionEvent event) {
        hideQuickBidModal();
    }

    private void hideQuickBidModal() {
        paneModalOverlay.setVisible(false);
        txtBidAmount.clear();
        targetItemId = -1;
    }

    @FXML
    private void handleConfirmQuickBid(ActionEvent event) {
        String amountText = txtBidAmount.getText().trim();
        if (amountText.isEmpty() || targetItemId == -1) return;

        try {
            double amount = Double.parseDouble(amountText);

            Map<String, Object> bidPayload = new HashMap<>();
            bidPayload.put("itemId", String.valueOf(targetItemId));
            bidPayload.put("bidderId", currentUsername);
            bidPayload.put("amount", amount);

            System.out.println("[Client History] Bắn lệnh Command.BID lên Server qua Modal nhanh: " + bidPayload);
            client.sendCommand(Command.BID, bidPayload);

        } catch (NumberFormatException e) {
            System.err.println("Giá tiền nhập vào không đúng định dạng!");
        } catch (IOException e) {
            System.err.println("Lỗi kết nối Socket: " + e.getMessage());
        }
    }

    @FXML
    private void handleBackAction(ActionEvent event) {
        client.setListener(null);
    }
}