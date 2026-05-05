package service;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import model.Items.*;

import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ItemCardController {

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
    public void setData(Item item){
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
        if (item.getImg() != null && !item.getImg().isEmpty()) {
            if (item.getImg().startsWith("http")) {
                j_img.setImage(new Image(item.getImg(), true));
            } else {
                // Thử load từ resource
                String imgPath = "/du_an_lon/img/" + item.getImg();
                java.net.URL imgUrl = getClass().getResource(imgPath);
                if (imgUrl != null) {
                    j_img.setImage(new Image(imgUrl.toExternalForm()));
                }
            }
        }
    }
    @FXML
    void on_choice(MouseEvent event) {}

}