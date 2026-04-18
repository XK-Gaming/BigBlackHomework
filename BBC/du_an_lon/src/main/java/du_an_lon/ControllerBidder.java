package du_an_lon;

import dao.DAOItems;
import dao.DAOUser;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import model.Items.Item;
import model.User.User;
import model.User.UserRole;
import model.User.UserSession;

import java.awt.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class ControllerBidder {
    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }
    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }
    @FXML
    private Button LogOut;

    @FXML
    private Pagination List_Items_Bid;

    @FXML
    private Label j_textSoDu;

    @FXML
    private ImageView j_image;

    @FXML
    private Label j_LabelName;
    private int itemsPerPage = 4; // Số lượng ô trên 1 trang
    private List<Item> allAssets; // Danh sách dữ liệu (ví dụ lấy từ DB)
    public void initialize() throws SQLException {
        User p1 = UserSession.getLoggedInUser();
        j_LabelName.setText(p1.getName());
        allAssets = DAOItems.selectedAll();

        // 2. Tính số lượng trang
        int pageCount = (int) Math.ceil((double) allAssets.size() / itemsPerPage);
        List_Items_Bid.setPageCount(pageCount);

        // 3. Cấu hình nội dung cho mỗi trang
        List_Items_Bid.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        // FlowPane là nơi chứa các ô Card, nó sẽ tự xuống dòng
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        flowPane.setPadding(new Insets(20));

        int start = pageIndex * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allAssets.size());

        for (int i = start; i < end; i++) {
            try {
                // Load file FXML của ô Card
                FXMLLoader loader = new FXMLLoader(getClass().getResource("AssetCard.fxml"));
                Node card = loader.load();

                // Đổ dữ liệu vào Card (giả sử có hàm setData trong CardController)
                ItemCardController controller = loader.getController();
                controller.setData(allAssets.get(i));

                flowPane.getChildren().add(card);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return flowPane;
    }





}




