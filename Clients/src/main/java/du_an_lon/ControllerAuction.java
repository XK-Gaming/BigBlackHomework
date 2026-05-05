package du_an_lon;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.AuctionEngine;
import model.auction.AuctionStatus;
import network.AuctionClient;
import network.DataPacket;
import network.ServerListener;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
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
        client.sendCommand("GET_AUCTION", item1 != null ? item1.getDatabaseId() : null);
        client.sendCommand("SET_AUCTION", Map.of("userId", p1.getUsername(), "itemId", item1.getDatabaseId()));
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
            j_description.setText(item1.getDescription() != null ? item1.getDescription() : "");
            renderImage();
            DecimalFormat df = new DecimalFormat("#,###");
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
                String imgPath = "/du_an_lon/img/" + item1.getImg();
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
        } else if (Double.parseDouble(priceText) <=  item1.getCurrentHighestPrice()) {
            j_notified.setText("Đặt giá không hợp lệ");
            j_notified.setVisible(true);
            return;
        }else{

        client.sendCommand("BID", Map.of(
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
        client.sendCommand("SET_AUCTION", Map.of("userId", p1.getUsername(), "itemId", ""));

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

    @Override
    public void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("GET_AUCTION_RESULT".equals(command)) {
            this_Auction = (Auction) response.getPayload();
            onAuctionDataLoaded(this_Auction);
        }
        else if ("BID_UPDATE".equals(command)) {
            Map<String, Object> update = (Map<String, Object>) response.getPayload();
            String itemId = String.valueOf(update.get("itemId"));
            if (item1 == null || !String.valueOf(item1.getDatabaseId()).equals(itemId)) {
                return;
            }

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
                if (this_Auction != null && bidderId != null && !bidderId.isEmpty()) {
                    this_Auction.setLeadingBidder(bidderId);
                }
                updatePriceAndLeader();
                j_notified.setText("Có lượt đặt giá mới trong phiên.");
                j_notified.setVisible(true);
            });
        }
        else if ("BID_RESULT".equals(command)) {
            Map<String, Object> result = (Map<String, Object>) response.getPayload();
            boolean isSuccess = (boolean) result.get("success");
            String message = (String) result.get("message");

            Platform.runLater(() -> {
                if (isSuccess) {
                    j_notified.setText("Đấu giá thành công");
                } else {
                    j_notified.setText(message);
                }
                j_notified.setVisible(true);
            });
        }
    }
}
