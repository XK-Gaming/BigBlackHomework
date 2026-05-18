package controller;


import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ControllerBidder implements ServerListener {
    private AuctionClient client = AuctionClient.getInstance();
    private boolean dataLoaded = false;

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
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
    private int itemsPerPage = 4;
    private List<Item> allAssets = new ArrayList<>();

    public void initialize() throws IOException {
        client.setListener(this);
        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
        }
        DecimalFormat df = new DecimalFormat("#,###");
        j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        List_Items_Bid.setPageCount(1);
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải danh sách sản phẩm...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        client.sendCommand(Command.SELECT_ITEMS, p1.getRole());
    }

    private void setupPagination() {
        if (allAssets == null || allAssets.isEmpty()) {
            List_Items_Bid.setPageCount(1);
            return;
        }

        int pageCount = (int) Math.ceil((double) allAssets.size() / itemsPerPage);
        List_Items_Bid.setPageCount(pageCount);
        List_Items_Bid.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        if (allAssets == null || allAssets.isEmpty()) {
            return new FlowPane();
        }

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        flowPane.setPadding(new Insets(20));

        int start = pageIndex * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allAssets.size());

        for (int i = start; i < end; i++) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/controller/AssetCard.fxml"));
                Node card = loader.load();
                Item data = allAssets.get(i);
                ItemCardController controller = loader.getController();
                controller.setData(data);
                card.setOnMouseClicked(event -> {
                    ItemSession.setLoggedInItem(data);
                    SceneHelper.changeScene((Node) event.getSource(), "View4.fxml");
                });
                flowPane.getChildren().add(card);
            } catch (IOException e) {
                System.err.println("Lỗi load FXML: " + e.getMessage());
                e.printStackTrace();
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        return flowPane;
    }


    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.getCommand();

        if (Command.SELECT_ITEMS_RESULT.equals(command)) {
            System.out.println(response.getPayload());
            allAssets = (ArrayList<Item>) response.getPayload();
            dataLoaded = true;

            Platform.runLater(() -> {
                setupPagination();
            });
        }
    }
}




