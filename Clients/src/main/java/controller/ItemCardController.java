package controller;

import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import model.Items.Item;
import network.AuctionEngine;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ItemCardController {
    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();
    private String watchToken;

    @FXML
    private AnchorPane cardRoot;
    @FXML
    private Label lblBadge;
    @FXML
    private Label j_EndTime;
    @FXML
    private Label j_StartPrice;
    @FXML
    private Label j_StartTime;
    @FXML
    private ImageView j_img;
    @FXML
    private Label j_name;
    @FXML
    private Label j_status;

    // HÀM MỚI: Chỉ nhận lệnh cập nhật trực tiếp nhãn giá tiền hiển thị độc lập
    public void updatePriceOnly(Item item) {
        DecimalFormat df = new DecimalFormat("#,###");
        double price = item.getCurrentHighestPrice();
        Platform.runLater(() -> {
            j_StartPrice.setText(df.format(price) + " VNĐ");
        });
    }

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

        String formattedString_Start = formatter.format(item.getAuctionStartTime());
        String formattedString_End = formatter.format(item.getAuctionEndTime());
        j_StartTime.setText(formattedString_Start);
        j_EndTime.setText(formattedString_End);

        if (item.getImg() != null && !item.getImg().isEmpty()) {
            if (item.getImg().startsWith("http")) {
                j_img.setImage(new Image(item.getImg(), true));
            } else {
                String imgPath = "/controller/img/" + item.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }

        watchToken = String.valueOf(auctionEngine.watchItem(item, (status, secondsToNext) -> Platform.runLater(() -> {
            switch (status) {
                case OPEN -> j_status.setText("CHƯA DIỄN RA (" + formatDuration(secondsToNext) + ")");
                case RUNNING -> j_status.setText("ĐANG DIỄN RA (" + formatDuration(secondsToNext) + ")");
                case FINISHED -> j_status.setText("ĐÃ KẾT THÚC");
                case PAID -> j_status.setText("ĐÃ THANH TOÁN");
                case CANCELLED -> j_status.setText("ĐÃ HỦY");
            }
        })));

        j_name.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null && watchToken != null) {
                auctionEngine.unwatch(watchToken);
                watchToken = null;
            }
        });

        // Setup dynamic badge overlay based on price/time
        if (lblBadge != null) {
            Instant now = Instant.now();
            if (item.getAuctionEndTime() != null &&
                    item.getAuctionEndTime().isAfter(now) &&
                    item.getAuctionEndTime().isBefore(now.plus(Duration.ofHours(2)))) {
                lblBadge.setText("⏰ SẮP HẾT HẠN");
                lblBadge.setStyle("-fx-background-color: #f59e0b; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 3 8 3 8; -fx-background-radius: 8;");
                lblBadge.setVisible(true);
            } else if (item.getCurrentHighestPrice() > item.getStartingPrice() * 1.2) {
                lblBadge.setText("🔥 HOT");
                lblBadge.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 3 8 3 8; -fx-background-radius: 8;");
                lblBadge.setVisible(true);
            } else if (item.getAuctionStartTime() != null &&
                    item.getAuctionStartTime().isBefore(now) &&
                    item.getAuctionStartTime().isAfter(now.minus(Duration.ofHours(2)))) {
                lblBadge.setText("🆕 MỚI ĐĂNG");
                lblBadge.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 9px; -fx-padding: 3 8 3 8; -fx-background-radius: 8;");
                lblBadge.setVisible(true);
            } else {
                lblBadge.setVisible(false);
            }
        }

        // Hover animations
        if (cardRoot != null) {
            cardRoot.setOnMouseEntered(event -> {
                cardRoot.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-effect: dropshadow(three-pass-box, rgba(37,99,235,0.25), 15, 0.2, 0, 8);");
                cardRoot.setTranslateY(-4);
            });
            cardRoot.setOnMouseExited(event -> {
                cardRoot.setStyle("-fx-background-color: white; -fx-background-radius: 14; -fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.12), 10, 0, 0, 5);");
                cardRoot.setTranslateY(0);
            });
        }
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