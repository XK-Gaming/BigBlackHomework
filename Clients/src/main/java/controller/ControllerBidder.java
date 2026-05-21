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
import javafx.util.Duration;
import model.Items.Item;
import model.Items.ItemSession;
import model.User.User;
import model.User.UserSession;
import model.auction.Auction;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;
import org.controlsfx.control.Notifications;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ControllerBidder implements ServerListener {
    private AuctionClient client = AuctionClient.getInstance();
    private boolean dataLoaded = false;
    User p1 = UserSession.getLoggedInUser();


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
    private void handleIncomingToastNotification(Object payload) {
        try {
            // 1. Giải mã gói tin từ Server
            Map<String, Object> notifData = (Map<String, Object>) payload;
            double newPrice = (Double) notifData.get("newPrice");
            Item item =(Item) notifData.get("item");
            Auction auction = (Auction) notifData.get("auction");

            for (Item surfItem : allAssets) {
                if (surfItem.getDatabaseId()== item.getDatabaseId()) {
                    // Cập nhật giá mới nhất cho Item này trong bộ nhớ
                    surfItem.setCurrentHighestPrice(newPrice);
                    break;
                }
            }

            // 3. Trả lại tiền cho người bị vượt giá (Người đặt giá trước đó)
            double oldPrice = auction.getBidHistory().get(auction.getBidHistory().size() - 2).getAmount();
            p1.setBalance(p1.getBalance() + oldPrice);

            Platform.runLater(() -> {
                DecimalFormat df = new DecimalFormat("#,###");
                j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");

                // 1. Tạo Layout HBox chứa nội dung thông báo
                javafx.scene.layout.HBox customToast = new javafx.scene.layout.HBox();
                customToast.setSpacing(12);
                customToast.setAlignment(Pos.CENTER_LEFT);

                // ĐỔI STYLE NỀN: Đổ bóng nhẹ (Drop Shadow), bo góc và bo viền màu đỏ nhạt để cảnh báo bị vượt giá
                customToast.setStyle(
                        "-fx-background-color: #FFFFFF; " +
                                "-fx-background-radius: 10px; " +
                                "-fx-border-color: #FFCDD2; " +
                                "-fx-border-radius: 10px; " +
                                "-fx-border-width: 1px; " +
                                "-fx-padding: 12px 16px; " +
                                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 8, 0, 0, 4);"
                );

                // 2. Icon chuông thông báo (Nền đỏ tròn, chuông trắng)
                javafx.scene.shape.Circle iconBg = new javafx.scene.shape.Circle(16, javafx.scene.paint.Color.web("#E53935"));
                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 14px;");
                StackPane iconPane = new StackPane(iconBg, icon);

                // 3. Phần chữ hiển thị (Tiêu đề + Nội dung)
                javafx.scene.layout.VBox textContainer = new javafx.scene.layout.VBox();
                textContainer.setSpacing(3);

                Label titleLabel = new Label("SẢN PHẨM BỊ VƯỢT GIÁ!");
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #B71C1C;");

                Label messageLabel = new Label("Sản phẩm " + item.getName()+" : "+ df.format(newPrice) + " VNĐ"  );
                messageLabel.setStyle("-fx-text-fill: #424242; -fx-font-size: 12px; -fx-font-family: 'Segoe UI', Arial;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(260);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconPane, textContainer);

                Notifications notificationBuilder = Notifications.create()
                        .owner(j_textSoDu)
                        .graphic(customToast) // Đưa customToast lại vào đây
                        .hideAfter(Duration.seconds(4))
                        .position(Pos.BOTTOM_RIGHT)
                        .onAction(event -> {
                            SenceChangeItem(item);
                        });

// Mẹo loại bỏ hoàn toàn khung xám và nút X mặc định của ControlsFX
                notificationBuilder.show();

// Chạy dòng này ngay sau .show() để xóa sạch các thành phần thừa, chỉ giữ lại khung trắng của bạn
                customToast.getParent().setStyle("-fx-background-color: transparent; -fx-padding: 0; -fx-effect: null;");
                customToast.getParent().getChildrenUnmodifiable().forEach(node -> {
                    if (node != customToast) {
                        node.setVisible(false); // Ẩn luôn nút Close (X) mặc định
                        node.setManaged(false);
                    }
                });
                setupPagination();
            });
        } catch (Exception e) {
            System.err.println("Lỗi khi hiển thị Toast Notification: " + e.getMessage());
        }
    }
    public void SenceChangeItem(Item item) {
    ItemSession.setLoggedInItem(item);
    SceneHelper.changeScene((Node)j_textSoDu, "View4.fxml");
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
        if(Command.NOTIFICATION.equals(command)){
            handleIncomingToastNotification(response.getPayload());
        }
    }
}




