package controller;


import javafx.fxml.FXML;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import model.Items.*;
import model.auction.AuctionEngine;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Controller cho một "thẻ item" (card) trong danh sách sản phẩm (FXML: `AssetCard.fxml`).
 *
 * <p>Trách nhiệm:
 * <ul>
 *   <li>Bind dữ liệu {@link Item} lên UI (tên, giá hiện tại, thời gian, ảnh).</li>
 *   <li>Đăng ký theo dõi trạng thái đấu giá bằng {@link AuctionEngine#watchItem} để cập nhật label trạng thái theo thời gian.</li>
 *   <li>Huỷ theo dõi (unwatch) khi card bị remove khỏi scene để tránh leak callback.</li>
 * </ul>
 */
public class ItemCardController {
    private final AuctionEngine auctionEngine = AuctionEngine.getInstance();

    /** Token dùng để huỷ đăng ký watch trong {@link AuctionEngine}. */
    private String watchToken;

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

    /**
     * Precondition: {@code item} khác null và có đủ thông tin tối thiểu (name, currentHighestPrice, start/end time).
     * Postcondition:
     * - UI labels được cập nhật theo dữ liệu item.
     * - Nếu có ảnh: load ảnh từ URL http(s) hoặc từ resource `/controller/img/`.
     * - Đăng ký watch trạng thái đấu giá cho item; token được lưu để unwatch khi cần.
     * NOTE:
     * - Watch callback chạy từ luồng nền -> chuyển về UI thread bằng {@code Platform.runLater}.
     * - Múi giờ hiển thị sử dụng {@code ZoneId.systemDefault()}.
     * Method returns: nothing.
     * @throws IOException NOTE: Có thể ném từ logic gọi/khai báo; hiện method không trực tiếp I/O ngoại trừ load ảnh URL.
     * @throws URISyntaxException NOTE: Khai báo theo signature hiện tại.
     */
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
    /**
     * Precondition: Không có.
     * Postcondition: Không có (placeholder handler; click logic thường gắn từ bên ngoài ở controller danh sách).
     * Method returns: nothing.
     */
    void on_choice(MouseEvent event) {}

    /**
     * Precondition: {@code totalSeconds >= 0}.
     * Postcondition: Không thay đổi state.
     * Method returns: Chuỗi định dạng {@code HH:mm:ss} dùng hiển thị countdown.
     */
    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}