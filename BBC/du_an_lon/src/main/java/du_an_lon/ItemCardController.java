package du_an_lon;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import model.Items.Item;

import java.io.ByteArrayInputStream;
import java.time.Instant;
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
        j_StartPrice.setText(String.valueOf(item.getStartingPrice()));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        // Quan trọng: Phải có .withZone để máy biết dùng múi giờ nào

        // 3. Chuyển đổi
        String formattedString_Start = formatter.format(item.getAuctionStartTime());
        String formattedString_End = formatter.format(item.getAuctionEndTime());
        j_StartTime.setText(formattedString_Start);
        j_EndTime.setText(formattedString_End);
        // chuyển tuqf byte[] --> định dạng img
        byte[] imageBytes = item.getImg();

        if (imageBytes != null && imageBytes.length > 0) {
            // 1. Chuyển mảng byte thành một luồng dữ liệu (Stream)
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);

            // 2. Tạo đối tượng Image từ luồng dữ liệu đó
            Image image = new Image(bis);

            // 3. Đưa ảnh lên giao diện
            j_img.setImage(image);
        }
        else{
            Image image = new Image(getClass().getResourceAsStream("/du_an_lon/img/img_item.png"));
            j_img.setImage(image);
        }
    }

}