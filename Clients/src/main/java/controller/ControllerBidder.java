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
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Sảnh bidder.
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

    @FXML
    private javafx.scene.shape.Circle connectionStatus;

    @FXML
    private Label connectionText;

    @FXML
    private TextField txtSearch;

    private ConnectionStatusManager statusManager;

    private static final int DEFAULT_ITEMS_PER_PAGE = 4;
    private static final double CARD_WIDTH = 210;
    private static final double CARD_HEIGHT = 280;
    private static final double CARD_HGAP = 20;
    private static final double CARD_VGAP = 20;
    private static final double PAGE_PADDING = 20;
    private static final String PAGINATION_STYLE = "-fx-background-color: white; "
            + "-fx-background-radius: 14; "
            + "-fx-effect: dropshadow(three-pass-box, rgba(15,23,42,0.08), 10, 0, 0, 5); "
            + "-fx-page-information-alignment: bottom; "
            + "-fx-animate-on-change: false;";

    private int itemsPerPage = DEFAULT_ITEMS_PER_PAGE;
    private List<Item> allAssets = new ArrayList<>();

    private List<Item> filteredAssets = new ArrayList<>();

    private final Map<Integer, ItemCardController> activeControllers = new HashMap<>();
    // Xử lý nút giao diện.
    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    // Đăng xuất.
    @FXML
    void On_LogOut(ActionEvent event) {

        try {
            if (p1 != null) {
                client.sendCommand(Command.LOGOUT, Map.of("username", p1.getUsername()));
            }
        } catch (IOException e) {
            System.err.println("Logout request failed: " + e.getMessage());
        }
    }

    // Xử lý nút giao diện.
    @FXML
    void On_ResetItems(ActionEvent event) {
        System.out.println("[Client] Người dùng yêu cầu làm mới danh sách...");

        if (txtSearch != null) {
            txtSearch.clear();
        }

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
    // Khởi tạo màn hình.
    public void initialize() throws IOException {
        AuctionClient.getInstance().addListener(this);

        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        User p1 = UserSession.getLoggedInUser();

        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }

        List_Items_Bid.setPageCount(1);
        List_Items_Bid.setStyle(PAGINATION_STYLE);
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải danh sách sản phẩm...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        List_Items_Bid.widthProperty().addListener((observable, oldValue, newValue) -> updateItemsPerPageForCurrentSize());
        List_Items_Bid.heightProperty().addListener((observable, oldValue, newValue) -> updateItemsPerPageForCurrentSize());
        Platform.runLater(this::updateItemsPerPageForCurrentSize);

        if (txtSearch != null) {
            txtSearch.textProperty().addListener((observable, oldValue, newValue) -> {
                handleSearch(newValue);
            });
        }

        if (p1 != null) {
            client.sendCommand(Command.SELECT_ITEMS, p1.getRole().toString());
        }
    }
    // Tìm kiếm sản phẩm.
    private void handleSearch(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {

            filteredAssets = new ArrayList<>(allAssets);
        } else {
            String lowerKey = keyword.toLowerCase().trim();
            filteredAssets = allAssets.stream()
                    .filter(item -> item.getName() != null && item.getName().toLowerCase().contains(lowerKey))
                    .toList();
        }

        setupPagination();
    }
    // Dựng phân trang.
    private void setupPagination() {
        int firstVisibleItemIndex = Math.max(0, List_Items_Bid.getCurrentPageIndex() * itemsPerPage);
        setupPagination(firstVisibleItemIndex);
    }
    // Dựng phân trang.
    private void setupPagination(int firstVisibleItemIndex) {
        itemsPerPage = calculateItemsPerPage();

        int pageCount = Math.max(1, (int) Math.ceil((double) filteredAssets.size() / itemsPerPage));
        List_Items_Bid.setPageCount(pageCount);

        List_Items_Bid.setPageFactory(null);
        List_Items_Bid.setPageFactory(this::createPage);

        int targetPage = Math.min(pageCount - 1, Math.max(0, firstVisibleItemIndex / itemsPerPage));
        List_Items_Bid.setCurrentPageIndex(targetPage);
    }
    // Cập nhật dữ liệu.
    private void updateItemsPerPageForCurrentSize() {
        int calculatedItemsPerPage = calculateItemsPerPage();
        if (calculatedItemsPerPage == itemsPerPage) {
            return;
        }

        int firstVisibleItemIndex = Math.max(0, List_Items_Bid.getCurrentPageIndex() * itemsPerPage);
        itemsPerPage = calculatedItemsPerPage;
        if (filteredAssets != null && !filteredAssets.isEmpty()) {
            setupPagination(firstVisibleItemIndex);
        }
    }
    // Tính toán dữ liệu.
    private int calculateItemsPerPage() {
        double width = List_Items_Bid.getWidth();
        double height = List_Items_Bid.getHeight();
        if (width <= 0 || height <= 0) {
            return Math.max(DEFAULT_ITEMS_PER_PAGE, itemsPerPage);
        }

        double availableWidth = Math.max(CARD_WIDTH, width - (PAGE_PADDING * 2));
        double availableHeight = Math.max(CARD_HEIGHT, height - (PAGE_PADDING * 2));
        int columns = Math.max(1, (int) Math.floor((availableWidth + CARD_HGAP) / (CARD_WIDTH + CARD_HGAP)));
        int rows = Math.max(1, (int) Math.floor((availableHeight + CARD_VGAP) / (CARD_HEIGHT + CARD_VGAP)));

        return Math.max(DEFAULT_ITEMS_PER_PAGE, columns * rows);
    }
    // Vẽ trang sản phẩm.
    private Node createPage(int pageIndex) {

        if (filteredAssets == null || filteredAssets.isEmpty()) {
            Label noItemLabel = new Label("Không tìm thấy sản phẩm nào phù hợp.");
            StackPane pane = new StackPane(noItemLabel);
            pane.setPrefHeight(400);
            return pane;
        }

        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(20);
        flowPane.setVgap(20);
        flowPane.setPadding(new Insets(20));
        flowPane.setAlignment(Pos.TOP_LEFT);
        flowPane.setPrefWrapLength(Math.max(CARD_WIDTH, List_Items_Bid.getWidth() - (PAGE_PADDING * 2)));

        int start = pageIndex * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filteredAssets.size());

        for (int i = start; i < end; i++) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AssetCard.fxml"));
                Node card = loader.load();
                Item data = filteredAssets.get(i);

                ItemCardController controller = loader.getController();
                controller.setData(data);

                int itemId = data.getDatabaseId();
                activeControllers.put(itemId, controller);

                card.setOnMouseClicked(event -> {
                    ItemSession.setLoggedInItem(data);
                    client.removeListener(this);
                    SceneHelper.changeScene((Node) event.getSource(), "/fxml/BiddingView.fxml");
                });
                flowPane.getChildren().add(card);
            } catch (Exception e) {
                System.err.println("Lỗi render AssetCard tại index " + i + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        List<Integer> allValidIds = filteredAssets.stream().map(Item::getDatabaseId).toList();
        activeControllers.keySet().retainAll(allValidIds);

        return flowPane;
    }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // Nhận danh sách sản phẩm.
        if (Command.SELECT_ITEMS_RESULT.equals(command) ||
                Command.ITEMS_UPDATE.equals(command) ||
                Command.BID_UPDATE.equals(command)) {

            System.out.println("[Client Debug] Nhận tín hiệu từ Server. Command: " + command);

            Object payload = response.payload();
            if (payload == null) {
                System.err.println("[Client Lỗi] Payload từ Server trả về bị null!");
                return;
            }

            if (payload instanceof List<?> rawList) {
                List<Item> updatedItems = new ArrayList<>();
                for (Object obj : rawList) {
                    processPayloadObject(obj, updatedItems);
                }
                this.allAssets = updatedItems;
                this.dataLoaded = true;

                Platform.runLater(() -> handleSearch(txtSearch != null ? txtSearch.getText() : ""));
            }
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
                        if (mapPayload.containsKey("deletedItemId") && Boolean.TRUE.equals(mapPayload.get("success"))) {
                            int deletedId = Integer.parseInt(mapPayload.get("deletedItemId").toString());
                            Platform.runLater(() -> removeSingleItem(deletedId));
                            return;
                        }

                        if (mapPayload.get("item") instanceof Item item) {
                            singleItemToUpdate = item;
                            if (mapPayload.get("newPrice") != null) {
                                double price = Double.parseDouble(mapPayload.get("newPrice").toString());
                                singleItemToUpdate.setCurrentHighestPrice(price);
                            }
                        }
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

        // Nhận thông báo realtime.
        if (Command.NOTIFICATION.equals(command)) {
            UserBalanceSync.applyAndRefresh(response.payload(), j_textSoDu);
            handleIncomingToastNotification(response.payload());
        }

        // Nhận cập nhật số dư.
        if (Command.BALANCE_UPDATE.equals(command) || Command.SET_AUTO_BID_RESULT.equals(command)) {
            UserBalanceSync.applyAndRefresh(response.payload(), j_textSoDu);
        }

        // Nhận lệnh đăng xuất cưỡng chế.
        if (Command.FORCE_LOGOUT.equals(command)) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Tài khoản bị xóa");
                alert.setHeaderText(null);
                alert.setContentText("Tài khoản của bạn đã bị Admin xóa. Ứng dụng sẽ tự đóng.");
                alert.showAndWait();
                System.exit(0);
            });
        }

        // Nhận kết quả đăng xuất.
        if (Command.LOGOUT_RESULT.equals(command)) {
            Platform.runLater(() -> {

                AuctionClient.getInstance().closeConnection();
                UserSession.cleanUserSession();
                client.removeListener(this);
                SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");

            });
        }
    }
    // Chuẩn hóa payload item.
    private void processPayloadObject(Object obj, List<Item> listToPopulate) {
        if (obj instanceof Item item) {
            listToPopulate.add(item);
        } else if (obj instanceof Auction a && a.getItem() != null) {
            a.getItem().setCurrentHighestPrice(a.getCurrentPrice());
            listToPopulate.add(a.getItem());
        }
    }
    // Cập nhật item realtime.
    private void updateSingleItem(Item updatedItem) {
        if (updatedItem == null) return;

        int targetId = updatedItem.getDatabaseId();
        boolean isExist = false;

        for (int i = 0; i < allAssets.size(); i++) {
            if (allAssets.get(i).getDatabaseId() == targetId) {
                allAssets.set(i, updatedItem);
                isExist = true;
                break;
            }
        }

        if (!isExist) {
            System.out.println("[UI Realtime] Phát hiện Item mới hoàn toàn! Thêm vào danh sách.");
            allAssets.add(updatedItem);

            handleSearch(txtSearch != null ? txtSearch.getText() : "");
        } else {

            if (activeControllers.containsKey(targetId)) {
                ItemCardController cardController = activeControllers.get(targetId);

                try {
                    cardController.setData(updatedItem);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                System.out.println("[UI Realtime] Đã ép thẻ ID " + targetId + " cập nhật giá mới trên màn hình!");
            } else {

                handleSearch(txtSearch != null ? txtSearch.getText() : "");
            }
        }
    }
    // Hiện toast realtime.
    private void handleIncomingToastNotification(Object payload) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            if (!(payload instanceof Map<?, ?>)) {
                System.err.println("[Client Lỗi] Payload notification không phải là Map!");
                return;
            }

            Map<?, ?> notifData = (Map<?, ?>) payload;

            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            String itemName = "Sản phẩm";
            StringBuilder details = new StringBuilder();
            Item item = null;
            Object itemObj = notifData.get("item");

            if (itemObj instanceof Item) {
                item = (Item) itemObj;
                itemName = item.getName();

                if (item.getDescription() != null) {
                    details.append(item.getDescription());
                }
            }
            else if (itemObj instanceof Map<?, ?> itemMap) {

                Object nameField = itemMap.get("name");
                if (nameField != null) itemName = nameField.toString();

                if (itemMap.containsKey("artist") && itemMap.get("artist") != null) {
                    details.append("\nTác giả: ").append(itemMap.get("artist"));
                }
                if (itemMap.containsKey("brand") && itemMap.get("brand") != null) {
                    details.append("\nThương hiệu: ").append(itemMap.get("brand"));
                }
                if (itemMap.containsKey("model") && itemMap.get("model") != null) {
                    details.append("\nDòng máy: ").append(itemMap.get("model"));
                }
                if (itemMap.containsKey("manufacturer") && itemMap.get("manufacturer") != null) {
                    details.append("\nHãng SX: ").append(itemMap.get("manufacturer"));
                }
                if (itemMap.containsKey("year") && itemMap.get("year") != null) {
                    details.append("\nNăm sản xuất: ").append(itemMap.get("year"));
                }
            }

            final double finalPrice = newPrice;
            final String finalItemName = itemName;
            final String finalDetails = details.toString();
            final Item finalItemObj = item;

            Platform.runLater(() -> {
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(320);
                customToast.setStyle("-fx-background-color: #FFFFFF;");

                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 85);
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
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #212121; -fx-font-family: 'Segoe UI', Arial;");

                String messageText = "Tên: " + finalItemName + "\nGiá hiện tại: " + df.format(finalPrice) + " VNĐ";

                Label messageLabel = new Label(messageText);
                messageLabel.setStyle("-fx-text-fill: #555555; -fx-font-size: 11px; -fx-font-family: 'Segoe UI', Arial;");
                messageLabel.setWrapText(true);
                messageLabel.setMaxWidth(220);

                textContainer.getChildren().addAll(titleLabel, messageLabel);
                customToast.getChildren().addAll(iconBlock, textContainer);

                Notifications notificationBuilder = Notifications.create()
                        .owner(j_textSoDu)
                        .graphic(customToast)
                        .hideAfter(Duration.seconds(5))
                        .position(Pos.BOTTOM_RIGHT);

                customToast.setOnMouseClicked(event -> {
                    if (finalItemObj != null) {
                        ItemSession.setLoggedInItem(finalItemObj);
                        client.removeListener(this);
                        SceneHelper.changeScene(j_textSoDu, "/fxml/BiddingView.fxml");
                    }
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
            System.err.println("Lỗi hiển thị thông báo Toast: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Xử lý nút giao diện.
    public void On_BidHistory(ActionEvent event) {
        client.removeListener(this);
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/BidHistoryView.fxml");
    }
    // Xóa item khỏi danh sách.
    private void removeSingleItem(int deletedId) {
        System.out.println("[UI Realtime] Phát hiện Item ID " + deletedId + " bị xóa từ Server. Đang dọn dẹp...");

        boolean isRemoved = allAssets.removeIf(item -> item.getDatabaseId() == deletedId);

        if (isRemoved) {
            activeControllers.remove(deletedId);

            handleSearch(txtSearch != null ? txtSearch.getText() : "");
        }
    }

}
