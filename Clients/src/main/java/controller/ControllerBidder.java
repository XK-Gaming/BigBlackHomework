package controller;

import model.Items.Item;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ControllerBidder implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private boolean dataLoaded = false;
    private final User p1 = UserSession.getLoggedInUser();

    @FXML
    private Button LogOut;

    @FXML
    private Button btnReset;

    @FXML
    private Pagination List_Items_Bid;

    @FXML
    private Label j_textSoDu;

    @FXML
    private ImageView j_image;

    @FXML
    private Label j_LabelName;

    private final int itemsPerPage = 4;
    private List<Item> allAssets = new ArrayList<>();

    /**
     * GIẢI PHÁP REALTIME: Quản lý các thẻ Card bằng Map động.
     * Lưu trữ toàn bộ controller đang hoạt động để đảm bảo dù ở trang ẩn vẫn được cập nhật dữ liệu ngầm.
     */
    private final Map<Integer, ItemCardController> activeControllers = new HashMap<>();

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");
    }

    @FXML
    void On_ResetItems(ActionEvent event) {
        System.out.println("[Client] Người dùng yêu cầu làm mới danh sách...");

        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải lại danh sách sản phẩm mới nhất...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        try {
            if (p1 != null) {
                client.sendCommand(Command.SELECT_ITEMS, p1.getRole().toString());
            } else {
                client.sendCommand(Command.SELECT_ITEMS, "");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initialize() throws IOException {
        client.setListener(this);

        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }

        List_Items_Bid.setPageCount(1);
        List_Items_Bid.setStyle("-fx-page-information-alignment: bottom; -fx-animate-on-change: false;");
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải danh sách sản phẩm...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        if (p1 != null) {
            client.sendCommand(Command.SELECT_ITEMS, p1.getRole().toString());
        }
    }

    private void setupPagination() {
        int pageCount = Math.max(1, (int) Math.ceil((double) allAssets.size() / itemsPerPage));
        List_Items_Bid.setPageCount(pageCount);
        List_Items_Bid.setPageFactory(this::createPage);
    }

    private Node createPage(int pageIndex) {
        if (allAssets == null || allAssets.isEmpty()) {
            Label noItemLabel = new Label("Hiện không có sản phẩm nào đang đấu giá.");
            StackPane pane = new StackPane(noItemLabel);
            pane.setPrefHeight(400);
            return pane;
        }

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        flowPane.setPadding(new Insets(20));

        int start = pageIndex * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allAssets.size());

        for (int i = start; i < end; i++) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AssetCard.fxml"));
                Node card = loader.load();
                Item data = allAssets.get(i);

                ItemCardController controller = loader.getController();
                controller.setData(data);

                int itemId = data.getDatabaseId();

                // Đăng ký/Cập nhật đè controller mới nhất cho ID tương ứng
                activeControllers.put(itemId, controller);

                card.setOnMouseClicked(event -> {
                    ItemSession.setLoggedInItem(data);
                    SceneHelper.changeScene((Node) event.getSource(), "/fxml/BiddingView.fxml");
                });
                flowPane.getChildren().add(card);
            } catch (Exception e) {
                System.err.println("Lỗi render AssetCard tại index " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Thay vì retainAll dựa trên trang hiện tại (làm mất realtime trang cũ),
        // ta dọn dẹp map dựa trên danh sách sản phẩm thực tế còn tồn tại từ Server.
        List<Integer> allValidIds = allAssets.stream().map(Item::getDatabaseId).toList();
        activeControllers.keySet().retainAll(allValidIds);

        return flowPane;
    }

    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        if (Command.SELECT_ITEMS_RESULT.equals(command) ||
                Command.ITEMS_UPDATE.equals(command) ||
                Command.BID_UPDATE.equals(command)) {

            System.out.println("[Client Debug] Nhận tín hiệu từ Server. Command: " + command);

            Object payload = response.payload();
            if (payload == null) {
                System.err.println("[Client Lỗi] Payload từ Server trả về bị null!");
                return;
            }

            // Trường hợp 1: Tải mới/Làm mới toàn bộ danh sách
            if (payload instanceof List<?> rawList) {
                List<Item> updatedItems = new ArrayList<>();
                for (Object obj : rawList) {
                    processPayloadObject(obj, updatedItems);
                }
                this.allAssets = updatedItems;
                this.dataLoaded = true;

                Platform.runLater(this::setupPagination);
            }
            // Trường hợp 2: Cập nhật biến động đơn lẻ Realtime từ các client khác đặt giá
            else {
                Item singleItemToUpdate = null;

                if (payload instanceof Item item) {
                    singleItemToUpdate = item;
                } else if (payload instanceof Auction auction && auction.getItem() != null) {
                    auction.getItem().setCurrentHighestPrice(auction.getCurrentPrice());
                    singleItemToUpdate = auction.getItem();
                }
                else if (payload instanceof Map<?, ?> mapPayload) {
                    try {
                        if (mapPayload.get("item") instanceof Item item) {
                            singleItemToUpdate = item;
                            if (mapPayload.get("newPrice") != null) {
                                double price = Double.parseDouble(mapPayload.get("newPrice").toString());
                                singleItemToUpdate.setCurrentHighestPrice(price);
                            }
                        }
                        // Dự phòng bóc tách sâu bằng itemId từ Server gửi về
                        else if (mapPayload.get("itemId") != null && mapPayload.get("newPrice") != null) {
                            int targetId = Integer.parseInt(mapPayload.get("itemId").toString());
                            double price = Double.parseDouble(mapPayload.get("newPrice").toString());

                            for (Item existingItem : allAssets) {
                                if (existingItem.getDatabaseId() == targetId) {
                                    existingItem.setCurrentHighestPrice(price);
                                    singleItemToUpdate = existingItem;
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[Client Lỗi] Lỗi bóc tách Map payload: " + e.getMessage());
                    }
                }

                if (singleItemToUpdate != null) {
                    Item finalItem = singleItemToUpdate;
                    Platform.runLater(() -> updateSingleItem(finalItem));
                }
            }
        }

        if (Command.NOTIFICATION.equals(command)) {
            handleIncomingToastNotification(response.payload());
        }
    }

    private void processPayloadObject(Object obj, List<Item> listToPopulate) {
        if (obj instanceof Item item) {
            listToPopulate.add(item);
        } else if (obj instanceof Auction a && a.getItem() != null) {
            a.getItem().setCurrentHighestPrice(a.getCurrentPrice());
            listToPopulate.add(a.getItem());
        }
    }

    private void updateSingleItem(Item updatedItem) {
        if (updatedItem == null) return;

        int targetId = updatedItem.getDatabaseId();

        // 1. Đồng bộ giá trị mới vào mảng dữ liệu gốc
        for (int i = 0; i < allAssets.size(); i++) {
            if (allAssets.get(i).getDatabaseId() == targetId) {
                allAssets.set(i, updatedItem);
                break;
            }
        }

        // 2. Lấy controller từ Map và cập nhật giao diện trực tiếp nếu thẻ đó đang được vẽ
        ItemCardController controller = activeControllers.get(targetId);

        if (controller != null) {
            try {
                controller.updatePriceOnly(updatedItem);
                System.out.println("[UI Realtime] Đã đổi giá thành công cho Item ID: " + targetId);
            } catch (Exception e) {
                System.err.println("Lỗi cập nhật giá lên Card: " + e.getMessage());
            }
        } else {
            // Log này sẽ xuất hiện khi thẻ ở trang khác chưa được render, nhưng dữ liệu allAssets đã lưu thành công!
            System.out.println("[UI Realtime] Item ID " + targetId + " đã cập nhật ngầm vào mảng dữ liệu (Thẻ đang ẩn ở trang khác).");
        }
    }

    private void handleIncomingToastNotification(Object payload) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            Map<String, Object> notifData = (Map<String, Object>) payload;

            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            Item item = (Item) notifData.get("item");
            final double finalPrice = newPrice;

            Platform.runLater(() -> {
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300);
                customToast.setStyle("-fx-background-color: #FFFFFF;");

                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;");

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                VBox textContainer = new VBox();
                textContainer.setSpacing(4);
                textContainer.setAlignment(Pos.CENTER_LEFT);
                textContainer.setPadding(new Insets(10, 10, 10, 15));
                HBox.setHgrow(textContainer, Priority.ALWAYS);

                Label titleLabel = new Label("SẢN PHẨM CÓ LƯỢT ĐẤU GIÁ MỚI!");
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

                Label messageLabel = new Label("Sản phẩm " + (item != null ? item.getName() : "") + " : " + df.format(finalPrice) + " VNĐ");
                messageLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', Arial;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(200);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconBlock, textContainer);

                Notifications notificationBuilder = Notifications.create()
                        .owner(j_textSoDu)
                        .graphic(customToast)
                        .hideAfter(Duration.seconds(4))
                        .position(Pos.BOTTOM_RIGHT);

                customToast.setOnMouseClicked(event -> {
                    ItemSession.setLoggedInItem(item);
                    SceneHelper.changeScene(j_textSoDu, "/fxml/BiddingView.fxml");
                });

                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void On_BidHistory(ActionEvent event) {
        client.setListener(null);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/BidHistoryView.fxml");
    }

    public void On_MyAuctions(ActionEvent event) {
    }
}