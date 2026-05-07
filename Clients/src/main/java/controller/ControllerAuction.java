package controller;
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

import java.io.IOException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * Controller cho màn hình phiên đấu giá của một {@link model.Items.Item} đã được chọn.
 *
 * <p>Chức năng chính:
 * <ul>
 *   <li>Hiển thị thông tin item (tên, mô tả, ảnh, giá hiện tại).</li>
 *   <li>Lấy thông tin {@link Auction} từ server bằng {@code GET_AUCTION}.</li>
 *   <li>Gửi bid bằng {@code BID} và nhận update real-time ({@code BID_UPDATE}).</li>
 *   <li>Chạy engine trạng thái thời gian (countdown) bằng {@link AuctionEngine} để khoá/mở nút đặt giá theo trạng thái.</li>
 * </ul>
 */
public class ControllerAuction implements ServerListener {
    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    /** Engine client-side để tính trạng thái OPEN/RUNNING/FINISHED theo thời gian. */
    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();

    /** User hiện tại (từ session). */
    User p1 = UserSession.getLoggedInUser();

    /** Item đang xem/đấu giá (từ session). */
    Item item1 = ItemSession.getLoggedInItem();

    /**
     * Phiên đấu giá đang hiển thị.
     * NOTE: static -> dùng chung giữa các instance controller; dễ gây side-effect nếu mở nhiều màn.
     */
    static Auction this_Auction;

    /** Token dùng để huỷ đăng ký watch trong {@link AuctionEngine}. */
    private String watchToken;

    /** Tránh xử lý kết thúc nhiều lần khi status engine callback lặp lại. */
    private boolean finishHandled;

    /**
     * Precondition: User và Item đã được set trong session trước khi chuyển sang màn này.
     * Postcondition:
     * - Đăng ký listener cho màn hiện tại.
     * - Điền ngay thông tin item từ session để tránh nháy UI.
     * - Gửi {@code GET_AUCTION} để load {@link Auction} từ server.
     * - Gửi {@code SET_AUCTION} để server biết client đang "theo dõi" item nào (phục vụ push {@code BID_UPDATE}).
     * NOTE: Nếu {@code p1} hoặc {@code item1} null có thể phát sinh {@link NullPointerException}.
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu gửi command lỗi.
     */
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

    /**
     * Precondition: {@code auction} là kết quả từ server (có thể null nếu server không tìm thấy).
     * Postcondition:
     * - Nếu null: khoá đặt giá và hiển thị trạng thái "chưa diễn ra".
     * - Nếu có dữ liệu: gọi {@link #setupUI()} để bật countdown và start engine.
     * NOTE: UI update chạy trên JavaFX thread bằng {@code Platform.runLater}.
     * Method returns: nothing.
     */
    public void onAuctionDataLoaded(Auction auction) {
        this_Auction = auction;
        Platform.runLater(() -> {
            if (this_Auction == null) {
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
                return;
            }
            try {
                setupUI();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Precondition: {@code this_Auction != null}, {@code p1 != null}, {@code item1 != null}.
     * Postcondition: UI được bind đầy đủ và status engine được start.
     * NOTE: Khai báo throws {@link SQLException} nhưng method không truy cập DB trực tiếp; có thể do code cũ.
     * Method returns: nothing.
     * @throws SQLException NOTE: Theo signature hiện tại.
     */
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

    /**
     * Precondition: {@code item1} khác null; {@code this_Auction} có thể null.
     * Postcondition: Label giá và người dẫn đầu được cập nhật.
     * NOTE: Khi {@code this_Auction == null}, leader hiển thị "—" và giá lấy từ {@link ItemSession}.
     * Method returns: nothing.
     */
    private void updatePriceAndLeader() {
        if (this_Auction == null) {
            // Nếu chưa có Auction, hiển thị giá từ ItemSession
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
            j_leadingBidder.setText("—");
            return;
        }
        DecimalFormat df = new DecimalFormat("#,###");
        j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        // Lấy tên người dẫn đầu
        String leader = this_Auction.getLeadingBidder();
        if (leader == null || leader.trim().isEmpty() || leader.equalsIgnoreCase("null")) {
            j_leadingBidder.setText("Chưa có");
        } else {
            j_leadingBidder.setText(leader.replace("\"", ""));
        }
    }

    /**
     * Precondition: {@code item1} khác null.
     * Postcondition: Nếu item có ảnh -> hiển thị ảnh từ URL hoặc resource.
     * NOTE: Nếu resource không tồn tại thì giữ nguyên image hiện tại (không crash).
     * Method returns: nothing.
     */
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
    /**
     * Precondition:
     * - {@code item1} và {@code p1} khác null
     * - {@code j_setPrice} chứa số hợp lệ (parseDouble được)
     * Postcondition:
     * - Nếu input rỗng/<= current price -> hiển thị lỗi, không gửi.
     * - Nếu hợp lệ -> gửi command {@code BID} lên server với itemId, bidderId, amount.
     * NOTE: Hiện code không bắt {@link NumberFormatException} khi parse giá; nếu nhập chữ sẽ crash.
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu gửi bid lỗi (stream/socket).
     */
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
    /**
     * Precondition: Người dùng đang ở màn đấu giá; có thể đang watch engine.
     * Postcondition:
     * - Huỷ watch countdown (cleanup)
     * - Chuyển về màn danh sách (View3.fxml)
     * - Xoá {@link ItemSession}
     * - Báo server ngừng theo dõi item bằng {@code SET_AUCTION} (itemId="").
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu gửi command lỗi.
     */
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

    /**
     * Precondition: {@code item1} khác null.
     * Postcondition: Đăng ký watch countdown từ {@link AuctionEngine} và cập nhật UI theo trạng thái.
     * NOTE: Khi status = FINISHED sẽ gọi {@link #handleFinishedAuction()} đúng 1 lần nhờ {@code finishHandled}.
     * Method returns: nothing.
     */
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

    /**
     * Precondition: {@code this_Auction} đã có leadingBidder (hoặc null).
     * Postcondition:
     * - Nếu user hiện tại là người thắng -> hiển thị chúc mừng và tự chuyển sang {@code ViewPaid.fxml} sau 3s.
     * - Nếu không -> hiển thị thông báo phiên đã kết thúc.
     * NOTE: Đây là xử lý UI-side; logic thắng thua nguồn gốc vẫn do server/Auction xác định.
     * Method returns: nothing.
     */
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
    /**
     * Precondition:
     * - {@code GET_AUCTION_RESULT}: payload là {@link Auction} (có thể null).
     * - {@code BID_UPDATE}: payload là {@code Map<String,Object>} có keys: itemId, newPrice, bidderId, auction (tuỳ).
     * - {@code BID_RESULT}: payload là {@code Map<String,Object>} có keys: success(boolean), message(String).
     * Postcondition:
     * - GET_AUCTION_RESULT: gọi {@link #onAuctionDataLoaded(Auction)}.
     * - BID_UPDATE: nếu update cho đúng item -> cập nhật giá, cập nhật leadingBidder và refresh UI.
     * - BID_RESULT: hiển thị thông báo thành công/thất bại.
     * NOTE: UI update đều dùng {@code Platform.runLater}.
     * Method returns: nothing.
     */
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
