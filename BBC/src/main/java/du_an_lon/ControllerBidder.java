package du_an_lon;

import java.io.IOException;
import java.util.List;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import service.AppServices;
import service.CatalogService;
import service.dto.CatalogItemView;

public class ControllerBidder {
    private static final int ITEMS_PER_PAGE = 4;

    private final CatalogService catalogService = AppServices.catalogService();

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

    private List<CatalogItemView> allAssets = List.of();

    public void initialize() {
        User user = UserSession.getLoggedInUser();
        if (user != null) {
            j_LabelName.setText(user.getFullName());
        }
        j_textSoDu.setText("--");
        allAssets = catalogService.listCatalogItems();

        int pageCount = allAssets.isEmpty() ? 1 : (int) Math.ceil((double) allAssets.size() / ITEMS_PER_PAGE);
        List_Items_Bid.setPageCount(pageCount);
        List_Items_Bid.setPageFactory(this::createPage);
    }

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
    }

    @FXML
    void On_LogOut(javafx.event.ActionEvent event) {
        UserSession.cleanUserSession();
        ItemSession.cleanItemSession();
        SceneHelper.changeScene(LogOut, "View1.fxml");
    }

    private Node createPage(int pageIndex) {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        flowPane.setPadding(new Insets(20));

        int start = pageIndex * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allAssets.size());
        for (int index = start; index < end; index++) {
            CatalogItemView data = allAssets.get(index);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("AssetCard.fxml"));
                Node card = loader.load();
                ItemCardController controller = loader.getController();
                controller.setData(data);
                card.setOnMouseClicked(event -> {
                    ItemSession.setLoggedInItem(data.item());
                    SceneHelper.changeScene((Node) event.getSource(), "View4.fxml");
                });
                flowPane.getChildren().add(card);
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        return flowPane;
    }
}
