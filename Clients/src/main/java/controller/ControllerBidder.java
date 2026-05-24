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
import java.util.List;
import java.util.Map;

public class ControllerBidder implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private boolean dataLoaded = false;
    User p1 = UserSession.getLoggedInUser();


    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");
    }

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

    private ConnectionStatusManager statusManager;

    private final int itemsPerPage = 4;
    private List<Item> allAssets = new ArrayList<>();

    // Sử dụng bộ nhớ tạm cho các controller hiển thị trên trang HIỆN TẠI
    private final List<ItemCardController> activeControllers = new ArrayList<>();

    @FXML
    void On_ResetItems(ActionEvent event) {
        System.out.println("[Client] Người dùng yêu cầu làm mới danh sách...");

        // Hiện thông báo tạm thời trên Pagination trong lúc đợi Server phản hồi
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải lại danh sách sản phẩm mới nhất...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        // Gửi lệnh lên Server
        try {
            client.sendCommand(Command.SELECT_ITEMS, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initialize() throws IOException {
        client.setListener(this);
        
        statusManager = new ConnectionStatusManager(connectionStatus, connectionText);
        statusManager.startMonitoring();

        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
        }
        DecimalFormat df = new DecimalFormat("#,###");
        j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        List_Items_Bid.setPageCount(1);
        List_Items_Bid.setStyle("-fx-page-information-alignment: bottom; -fx-animate-on-change: false;");
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải danh sách sản phẩm...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        client.sendCommand(Command.SELECT_ITEMS, p1.getRole());
    }

    /**
     * SỬA LỖI: Buộc vẽ lại toàn bộ danh sách mới khi Reset hoặc nạp dữ liệu hàng loạt
     */
    private void setupPagination() {
        // Nếu không có item nào, tối thiểu vẫn phải giữ 1 trang trống để tránh crash Pagination
        int pageCount = Math.max(1, (int) Math.ceil((double) allAssets.size() / itemsPerPage));
        List_Items_Bid.setPageCount(pageCount);

        // Luôn luôn thiết lập lại PageFactory để làm mới giao diện hoàn toàn, xóa bỏ label "Đang tải..."
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

        // Xóa danh sách controller cũ của trang trước đó
        activeControllers.clear();

        for (int i = start; i < end; i++) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AssetCard.fxml"));
                Node card = loader.load();
                Item data = allAssets.get(i);

                ItemCardController controller = loader.getController();
                controller.setData(data);

                // Lưu lại các controller ĐANG HIỂN THỊ TRÊN MÀN HÌNH để cập nhật realtime đơn lẻ
                activeControllers.add(controller);

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
        return flowPane;
    }
    private void handleIncomingToastNotification(Object payload) {
        DecimalFormat df = new DecimalFormat("#,###");
        try {
            // 1. Giải mã gói tin từ Server an toàn
            Map<String, Object> notifData = (Map<String, Object>) payload;

            double newPrice = 0;
            Object priceObj = notifData.get("newPrice");
            if (priceObj instanceof Number) {
                newPrice = ((Number) priceObj).doubleValue();
            }

            Item item = (Item) notifData.get("item");
            final double finalPrice = newPrice;

            // 2. Đẩy việc hiển thị lên UI Thread của JavaFX
            Platform.runLater(() -> {

                // [THAY ĐỔI]: Tạo Layout chính ôm nội dung, bỏ viền và bóng đổ để hòa làm một với khung ngoài
                HBox customToast = new HBox();
                customToast.setAlignment(Pos.CENTER_LEFT);
                customToast.setPrefWidth(300); // Thu nhỏ lại một chút để vừa vặn với khung chứa của ControlsFX
                customToast.setStyle("-fx-background-color: #FFFFFF;"); // Chỉ cần nền trắng đơn giản

                // 3. Khối Icon bên trái (Nền màu xanh dương đậm)
                StackPane iconBlock = new StackPane();
                iconBlock.setPrefSize(60, 70);
                iconBlock.setStyle("-fx-background-color: #1565C0;"); // Khung ngoài sẽ tự bo góc nên ở đây để vuông

                Label icon = new Label("🔔");
                icon.setStyle("-fx-text-fill: white; -fx-font-size: 22px;");
                iconBlock.getChildren().add(icon);

                // 4. Phần chữ hiển thị (VBox) ở giữa
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

                // [THAY ĐỔI]: Chỉ thêm khối icon và khối chữ vào layout (Đã loại bỏ nút x tự chế)
                customToast.getChildren().addAll(iconBlock, textContainer);

                // 5. Khởi tạo ControlsFX và tận dụng hệ thống mặc định
                Notifications notificationBuilder = Notifications.create()
                        .owner(j_textSoDu) // Neo theo ứng dụng của bạn
                        .graphic(customToast) // Nhúng nội dung custom vào
                        .hideAfter(Duration.seconds(4)) // Tự động ẩn sau 4 giây
                        .position(Pos.BOTTOM_RIGHT); // Xuất hiện góc dưới bên phải
                customToast.setOnMouseClicked(event -> {
                    // Gọi hàm xử lý chuyển trang của bạn ở đây
                    // Ví dụ: chuyenDenTrangChiTietSanPham(item.getId());
                    ItemSession.setLoggedInItem(item);
                    System.out.println(item.getAuctionEndTime());
                    System.out.println(item.getAuctionStartTime());
                    System.out.println(item.getAuctionStatus());
                    SceneHelper.changeScene(j_textSoDu, "/fxml/BiddingView.fxml");
                });

                // [MẸO ĐẸP]: Xóa bỏ padding thừa của khung ngoài để khối màu xanh sát rạt ra rìa trái
                customToast.sceneProperty().addListener((observable, oldScene, newScene) -> {
                    if (newScene != null) {
                        newScene.windowProperty().addListener((obsWin, oldWin, newWin) -> {
                            if (newWin != null) {
                                javafx.scene.Node notificationPopup = newScene.getRoot().lookup(".notification-popup");
                                if (notificationPopup != null) {
                                    // Ép padding về 0 để phần màu xanh bám sát viền trái ngoài cùng
                                    notificationPopup.setStyle("-fx-padding: 0;");
                                }
                            }
                        });
                    }
                });

                // Hiển thị thông báo lên màn hình
                notificationBuilder.show();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();
        if (Command.SELECT_ITEMS_RESULT.equals(command) || Command.ITEMS_UPDATE.equals(command)) {
            System.out.println("[Client Debug] Nhận tín hiệu làm mới danh sách. Command: " + command);

            Object payload = response.payload();
            if (payload == null) {
                System.err.println("[Client Lỗi] Payload từ Server trả về bị null!");
                return;
            }
            // TRƯỜNG HỢP 1: Nhận toàn bộ danh sách (Khi mới vào app hoặc khi bấm RESET)
            if (payload instanceof List<?> rawList) {
                List<Item> updatedItems = new ArrayList<>();
                for (Object obj : rawList) {
                    processPayloadObject(obj, updatedItems);
                }
                this.allAssets = updatedItems;
                this.dataLoaded = true;

                Platform.runLater(this::setupPagination);
            }
            // TRƯỜNG HỢP 2: Cập nhật Realtime cho một item đơn lẻ (Ai đó vừa đặt giá)
            else {
                Item singleItemToUpdate = null;

                if (payload instanceof Item item) {
                    singleItemToUpdate = item;
                } else if (payload instanceof Auction auction && auction.getItem() != null) {
                    auction.getItem().setCurrentHighestPrice(auction.getCurrentPrice());
                    singleItemToUpdate = auction.getItem();
                }

                if (singleItemToUpdate != null) {
                    Item finalItem = singleItemToUpdate;
                    Platform.runLater(() -> updateSingleItem(finalItem));
                }
            }
            Platform.runLater(() -> {
                setupPagination();
            });
        }
        if(Command.NOTIFICATION.equals(command)){
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

        int targetIndex = -1;
        for (int i = 0; i < allAssets.size(); i++) {
            if (allAssets.get(i).getDatabaseId() == updatedItem.getDatabaseId()) {
                allAssets.set(i, updatedItem); // Cập nhật ngầm trong Database local (allAssets)
                targetIndex = i;
                break;
            }
        }

        if (targetIndex == -1) return;

        // Tính toán xem Item vừa thay đổi giá có nằm trong TRANG HIỆN TẠI đang hiển thị không
        int currentPage = List_Items_Bid.getCurrentPageIndex();
        int start = currentPage * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allAssets.size());

        if (targetIndex >= start && targetIndex < end) {
            int controllerIndex = targetIndex - start;

            // Kiểm tra an toàn xem controller có khớp và tồn tại trên UI không
            if (controllerIndex >= 0 && controllerIndex < activeControllers.size()) {
                ItemCardController controller = activeControllers.get(controllerIndex);
                try {
                    controller.setData(updatedItem);
                    System.out.println("[UI Realtime] Đã cập nhật nhanh Item ID: " + updatedItem.getDatabaseId());
                } catch (Exception e) {
                    System.err.println("Lỗi cập nhật nhanh Card tại vị trí " + controllerIndex + ": " + e.getMessage());
                }
            }
        }
    }

    public void On_BidHistory(ActionEvent event) {
        client.setListener(null); // Gỡ listener cũ để tránh rò rỉ bộ nhớ (Memory Leak)
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/BidHistoryView.fxml");
    }

    public void On_MyAuctions(ActionEvent event) {
    }
}