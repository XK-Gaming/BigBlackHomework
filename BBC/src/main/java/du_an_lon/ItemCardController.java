package du_an_lon;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import model.Items.Item;
import service.dto.CatalogItemView;

public class ItemCardController {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

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

    public void setData(CatalogItemView data) {
        Item item = data.item();
        j_name.setText(item.getName());
        j_StartPrice.setText(String.format("%,.0f VND", item.getCurrentHighestPrice()));
        j_StartTime.setText(FORMATTER.format(item.getStartTime()));
        j_EndTime.setText(FORMATTER.format(item.getEndTime()));
        FxViewUtils.loadImage(j_img, data.imagePath());
    }

    @FXML
    void on_choice(javafx.scene.input.MouseEvent event) {
    }
}
