package controller;


import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import model.Items.*;
import network.AuctionEngine;
import model.auction.AuctionStatus;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;


public class ItemCardController {
    private String watchToken;
    @FXML
    private Label j_EndTime;
    @FXML
    private Label j_StartPrice;
    @FXML
    private Label j_StartTime;

    @FXML
    private ImageView j_img;

    @FXML    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();
    @FXML
    private Label j_name;

    @FXML
    private Label j_status;

    public void setData(Item item) throws IOException, URISyntaxException {
        if (watchToken != null) {
            auctionEngine.unwatch(watchToken);
            watchToken = null;
        }
        j_name.setText(item.getName());
        DecimalFormat df = new DecimalFormat("#,###");
        double price = item.getCurrentHighestPrice();
        j_StartPrice.setText(df.format(price) + " VNĐ");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        // Quan trọng: Phải có .withZone để máy biết dùng múi giờ nào

        // 3. Chuyển đổi
        String formattedString_Start = formatter.format(item.getAuctionStartTime());
        String formattedString_End = formatter.format(item.getAuctionEndTime());
        j_StartTime.setText(formattedString_Start);
        j_EndTime.setText(formattedString_End);
        // 1. Link ảnh từ Cloudinary
        if(item.getImg() != null && !item.getImg().isEmpty()){
            if (item.getImg().startsWith("http")) {
                j_img.setImage(new Image(item.getImg(), true));
            } else {
                // Thử load từ resource (đảm bảo ảnh được copy vào target khi build)
                String imgPath = "/controller/img/" + item.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }

        watchToken = auctionEngine.watchItem(item, (status, secondsToNext) -> Platform.runLater(() -> {
            switch (status) {
                case OPEN -> j_status.setText("CHƯA DIỄN RA (" + formatDuration(secondsToNext) + ")");
                case RUNNING -> j_status.setText("ĐANG DIỄN RA (" + formatDuration(secondsToNext) + ")");
                case FINISHED -> j_status.setText("ĐÃ KẾT THÚC");
                case PAID -> j_status.setText("ĐÃ THANH TOÁN");
                case CANCELED -> j_status.setText("ĐÃ HỦY");
            }
        }));

        j_name.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null && watchToken != null) {
                auctionEngine.unwatch(watchToken);
                watchToken = null;
            }
        });
    }

    @FXML
    void on_choice(MouseEvent event) {}

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}