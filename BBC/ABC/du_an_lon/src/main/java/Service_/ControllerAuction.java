package Service_;

import dao.DAOAution_Items;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import model.auction.AuctionService;
import model.auction.AuctionStatus;

import java.io.File;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;

public class ControllerAuction {
    private final AuctionService auctionService = new AuctionService();
    User p1 = UserSession.getLoggedInUser();
    Item item1 = ItemSession.getLoggedInItem();
    static Auction this_Auction;
    long totalSeconds;
    private Timeline timeline;
    public void initialize() throws SQLException {
        this_Auction = auctionService.getAuction(item1);
        j_LabelName.setText(p1.getName());
        j_name.setText(item1.getName());
        renderImage();
        j_description.setText(item1.getDescription());
        updatePriceAndLeader();
        if(this_Auction.getStatus() == AuctionStatus.OPEN){
            j_status.setText("CHƯA DIỄN RA");
        Alarm(totalSeconds = java.time.Duration.between(Instant.now(),item1.getAuctionStartTime()).getSeconds());}
        // Giả sử item1 là đối tượng Item hiện tại
        if(this_Auction.getStatus() == AuctionStatus.FINISHED){
            j_status.setText("ĐÃ KẾT THÚC");
            j_days.setText(String.format("%02d", 0));
            j_hours.setText(String.format("%02d", 0));
            j_mins.setText(String.format("%02d", 0));
            j_secs.setText(String.format("%02d", 0));
            j_apply.setDisable(true);
            if (this_Auction.getLeadingBidder() != null &&
                    this_Auction.getLeadingBidder().equals(p1.getUsername())) {

                j_notified.setText("Chúc mừng! Bạn đã thắng. Đang chuyển đến trang thanh toán...");
                j_notified.setVisible(true);

                // Tạo một khoảng trễ nhỏ (vd: 3 giây) để người dùng kịp đọc thông báo trước khi chuyển trang
                PauseTransition delay = new PauseTransition(Duration.seconds(3));
                delay.setOnFinished(e -> {
                    // Gọi hàm chuyển sang View thanh toán
                    SceneHelper.changeScene(j_apply, "ViewPaid.fxml");
                });
                delay.play();

            } else {
                j_notified.setText("Phiên đấu giá đã kết thúc.");
                j_notified.setVisible(true);
            }}
        Instant now = Instant.now();
        Instant end = item1.getAuctionEndTime();
        // Chuyển từ Timestamp sang Instant
        totalSeconds = java.time.Duration.between(now,end).getSeconds();
        if(this_Auction.getStatus() != AuctionStatus.OPEN && this_Auction.getStatus() != AuctionStatus.FINISHED) {
            startCountdown();
        }



        }
    private void updatePriceAndLeader() {
        DecimalFormat df = new DecimalFormat("#,###");
        j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        String leader = (this_Auction.getLeadingBidder() != null)
                ? this_Auction.getLeadingBidder()
                : this_Auction.getDefaultBidder();
        j_leadingBidder.setText(leader);
    }

    private void renderImage() {
        File file = new File("src/main/java/imgs/" + item1.getImg());
        if (file.exists()) {
            j_img.setImage(new Image(file.toURI().toString()));
        }
    }
    @FXML
    private TextField j_setPrice;
    @FXML
    private Button j_apply;

    @FXML
    private Label j_leadingBidder;

    @FXML
    void On_apply(ActionEvent event) {
        j_notified.setVisible(false);

        // Gọi Service xử lý bid
        String result = auctionService.processBid(this_Auction, item1, p1, j_setPrice.getText());
        if (result.equals("SUCCESS")) {
            updatePriceAndLeader();
            j_notified.setText("Đấu giá thành công");
        } else {
            j_notified.setText(result);
        }
        j_notified.setVisible(true);
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
    void On_Return(ActionEvent event) {
        cleanup();
        SceneHelper.changeScene(j_return, "View3.fxml");
        ItemSession.cleanItemSession();
    }
    @FXML
    private Label j_CurrentPrice;

    @FXML
    private Label j_notified;
    private void startCountdown() {
        final long[] time = { this.totalSeconds };

        // --- BƯỚC QUAN TRỌNG: Gọi hiển thị lần đầu ngay lập tức ---
        updateTimerLabels(time[0]);

        Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);

        KeyFrame keyFrame = new KeyFrame(Duration.seconds(1), event -> {
            time[0]--;
                if (time[0] <= 0) {
                    timeline.stop();
                    j_days.setText(String.format("%02d", 0));
                    j_hours.setText(String.format("%02d", 0));
                    j_mins.setText(String.format("%02d", 0));
                    j_secs.setText(String.format("%02d", 0));
                    this_Auction.setStatus(AuctionStatus.FINISHED);
                    DAOAution_Items.getInstance().Update_Status(this_Auction, item1, AuctionStatus.FINISHED);

                    // Cập nhật giao diện
                    j_status.setText("FINISHED");
                    j_apply.setDisable(true);

                    // Kiểm tra xem người đang đăng nhập có phải là người thắng không
                    if (this_Auction.getLeadingBidder() != null &&
                            this_Auction.getLeadingBidder().equals(p1.getUsername())) {

                        j_notified.setText("Chúc mừng! Bạn đã thắng. Đang chuyển đến trang thanh toán...");
                        j_notified.setVisible(true);

                        // Tạo một khoảng trễ nhỏ (vd: 3 giây) để người dùng kịp đọc thông báo trước khi chuyển trang
                        PauseTransition delay = new PauseTransition(Duration.seconds(3));
                        delay.setOnFinished(e -> {
                            // Gọi hàm chuyển sang View thanh toán
                            SceneHelper.changeScene(j_apply, "ViewPaid.fxml");
                        });
                        delay.play();

                    } else {
                        j_notified.setText("Phiên đấu giá đã kết thúc.");
                        j_notified.setVisible(true);
                    }
                }

            // Gọi hàm cập nhật mỗi giây
            updateTimerLabels(time[0]);
        });

        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
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
    private void Alarm(long totalSeconds) {
        final long[] time = {totalSeconds };

        Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);

        KeyFrame keyFrame = new KeyFrame(Duration.seconds(1), event -> {
            time[0]--;

            if (time[0] <= 0) {
                cleanup();

                // 2. Dùng Platform.runLater yêu cầu hệ thống chờ giao diện hiển thị xong xuôi mới chạy
                javafx.application.Platform.runLater(() -> {
                    // 3. Kiểm tra chốt chặn cuối cùng cho an toàn
                    if (j_description.getScene() != null && j_description.getScene().getWindow() != null) {
                        SceneHelper.changeScene(j_description, "View4.fxml");
                    } else {
                        System.out.println("Cảnh báo: Không thể chuyển trang vì giao diện chưa được gắn vào Window!");
                    }
                });
            }
        });


                timeline.getKeyFrames().add(keyFrame);
                timeline.play();


    }

    @FXML
    private Label j_mins;
    @FXML
    private Label j_hours;
    @FXML
    private Label j_secs;
    @FXML
    private Label j_status;
    public void cleanup() {
        if (timeline != null) {
            timeline.stop();
            timeline.getKeyFrames().clear();
            timeline = null;
        }
    }

}
