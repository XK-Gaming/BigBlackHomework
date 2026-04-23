package du_an_lon;

import dao.DAOAution_Items;
import dao.DAOItems;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
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
import model.User.Bidder;
import model.User.Seller;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Instant;

public class ControllerAuction {
    User p1 = UserSession.getLoggedInUser();
    Item item1 = ItemSession.getLoggedInItem();
    static Auction this_Auction;
    long totalSeconds;
    public void initialize() throws SQLException {
        System.out.println(item1.getAuctionEndTime());
        if(DAOAution_Items.getInstance().selectByItemId(item1) == null){
            this_Auction = new Auction("1",item1,item1.getSellerId(), Instant.now());
        }
        else{this_Auction = DAOAution_Items.getInstance().selectByItemId(item1);}

        j_LabelName.setText(p1.getName());
        j_name.setText(item1.getName());


            // 3. Đưa ảnh lên giao diện
        String path = "src/main/java/imgs/" + item1.getImg(); // Hoặc đường dẫn bạn lưu
        File file = new File(path);
        if (file.exists()) {
            Image img = new Image(file.toURI().toString());
            j_img.setImage(img);}

        j_description.setText(item1.getDescription());
        DecimalFormat df = new DecimalFormat("#,###");
        j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        if(this_Auction.getLeadingBidder() != null){
            j_leadingBidder.setText(this_Auction.getLeadingBidder().getUsername());}
        else {j_leadingBidder.setText(this_Auction.getDefaultBidder());}
        // Giả sử item1 là đối tượng Item hiện tại
        Instant now = Instant.now();
        Instant end = item1.getAuctionEndTime(); // Chuyển từ Timestamp sang Instant
        this.totalSeconds = java.time.Duration.between(now, end).getSeconds();
        if(totalSeconds > 0){
            startCountdown();
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
        Double set_Price = Double.parseDouble(j_setPrice.getText());
        if(set_Price>= item1.getCurrentHighestPrice()){
            item1.setCurrentHighestPrice(set_Price);
            this_Auction.setLeadingBidder((Bidder) p1);
            DAOAution_Items.getInstance().Update(this_Auction, item1,(Bidder) p1);
            DAOItems.getInstance().Update(item1,set_Price);
            DecimalFormat df = new DecimalFormat("#,###");
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
            if(this_Auction.getLeadingBidder() != null){
                j_leadingBidder.setText(this_Auction.getLeadingBidder().getUsername());}
            else {j_leadingBidder.setText(this_Auction.getDefaultBidder());}
            j_notified.setText("Đấu giá thành công");
            j_notified.setVisible(true);
        }
        else{
            j_notified.setText("Đấu giá không hợp lệ");
            j_notified.setVisible(true);
        }
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
                j_notified.setText("Hết giờ!");
                return;
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
    @FXML
    private Label j_mins;
    @FXML
    private Label j_hours;
    @FXML
    private Label j_secs;
}
