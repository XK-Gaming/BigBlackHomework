package du_an_lon;

import dao.DAOItems;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import model.Items.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.text.DecimalFormat;
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
        String path = "src/main/java/imgs/" + item.getImg(); // Hoặc đường dẫn bạn lưu
        File file = new File(path);
        if (file.exists()) {
            Image img = new Image(file.toURI().toString());
            j_img.setImage(img);
    }
    }
    @FXML
    void on_choice(MouseEvent event) {}

}