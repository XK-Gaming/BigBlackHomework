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
import model.auction.Auction;
import network.AuctionClient;
import network.Command;
import network.DataPacket;
import network.ServerListener;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ControllerBidder implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private boolean dataLoaded = false;

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

    // Sử dụng bộ nhớ tạm cho các controller hiển thị trên trang HIỆN TẠI
    private final List<ItemCardController> activeControllers = new ArrayList<>();

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        SceneHelper.changeScene(LogOut, "/fxml/LoginView.fxml");
    }

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
        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
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

        client.sendCommand(Command.SELECT_ITEMS, "");
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