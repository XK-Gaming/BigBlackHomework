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
import network.DataPacket;
import network.ServerListener;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller cho màn hình người đấu giá (Bidder) xem danh sách sản phẩm.
 *
 * <p>Chức năng chính:
 * <ul>
 *   <li>Yêu cầu server trả danh sách item: {@code SELECT_ITEMS}.</li>
 *   <li>Nhận {@code SELECT_ITEMS_RESULT} và hiển thị dạng phân trang.</li>
 *   <li>Cho phép click vào item để vào màn hình chi tiết/đấu giá (set {@link ItemSession}).</li>
 * </ul>
 */
public class ControllerBidder implements ServerListener {
    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    /** Cờ trạng thái đã load dữ liệu từ server (hiện tại chỉ set, chưa dùng tiếp). */
    private boolean dataLoaded = false;

    /**
     * Precondition: {@code mouseEvent.getSource()} là một {@link Node} trong scene hiện tại.
     * Postcondition: Chuyển sang màn {@code View5.fxml}.
     * NOTE: Màn {@code View5.fxml} có vẻ là màn xem ảnh/chi tiết (tuỳ thiết kế).
     * Method returns: nothing.
     * @throws ClassCastException NOTE: Nếu source không phải {@link Node}.
     */
    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
    }

    @FXML
    /**
     * Precondition: {@code LogOut} thuộc scene hiện tại.
     * Postcondition: Chuyển về màn {@code View1.fxml}.
     * NOTE: Ở đây chưa dọn session; nếu cần logout "thật" hãy gọi {@link model.User.UserSession#cleanUserSession()}.
     * Method returns: nothing.
     */
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

    /**
     * Precondition: Các trường @FXML đã inject; client đã connect (hoặc sẽ connect trước đó).
     * Postcondition:
     * - Đăng ký listener cho màn hiện tại
     * - Set thông tin user lên UI (nếu có session)
     * - Hiển thị placeholder "đang tải"
     * - Gửi {@code SELECT_ITEMS} lên server để lấy danh sách
     * Method returns: nothing.
     * @throws IOException NOTE: Ném ra nếu gửi command lỗi (mất kết nối/stream lỗi).
     */
    public void initialize() throws IOException {
        client.setListener(this);
        User p1 = UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
        }
        List_Items_Bid.setPageCount(1);
        List_Items_Bid.setPageFactory(pageIndex -> {
            Label msg = new Label("Đang tải danh sách sản phẩm...");
            StackPane pane = new StackPane(msg);
            StackPane.setAlignment(msg, Pos.CENTER);
            pane.setPrefHeight(400);
            return pane;
        });

        client.sendCommand("SELECT_ITEMS", "");
    }

    /**
     * Precondition: {@code allAssets} đã được gán từ response.
     * Postcondition: Cập nhật cấu hình {@link Pagination}: số trang và pageFactory.
     * NOTE: Nếu danh sách rỗng thì set pageCount = 1 để tránh lỗi UI.
     * Method returns: nothing.
     */
    private void setupPagination() {
        if (allAssets == null || allAssets.isEmpty()) {
            List_Items_Bid.setPageCount(1);
            return;
        }

        int pageCount = (int) Math.ceil((double) allAssets.size() / itemsPerPage);
        List_Items_Bid.setPageCount(pageCount);
        List_Items_Bid.setPageFactory(this::createPage);
    }

    /**
     * Precondition: {@code pageIndex} trong khoảng [0, pageCount-1]; {@code allAssets} đã có dữ liệu.
     * Postcondition: Tạo một {@link FlowPane} chứa các card item cho trang tương ứng; mỗi card có handler click để chuyển màn.
     * NOTE: Click item sẽ set {@link ItemSession#setLoggedInItem(Item)} để màn sau đọc dữ liệu.
     * Method returns: {@link Node} đại diện UI của trang.
     * NOTE: {@link URISyntaxException} được wrap thành {@link RuntimeException} (sẽ crash nếu xảy ra).
     */
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


    /**
     * Precondition: Với {@code SELECT_ITEMS_RESULT} thì payload là {@code ArrayList<Item>}.
     * Postcondition: Lưu list vào {@code allAssets}, set cờ {@code dataLoaded}, và cập nhật pagination trên UI thread.
     * NOTE: UI update dùng {@code Platform.runLater} vì callback có thể chạy trên luồng nền.
     * Method returns: nothing.
     * @throws ClassCastException NOTE: Nếu payload không phải {@code ArrayList<Item>}.
     */
    @Override
    public void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("SELECT_ITEMS_RESULT".equals(command)) {
            System.out.println(response.getPayload());
            allAssets = (ArrayList<Item>) response.getPayload();
            dataLoaded = true;

            Platform.runLater(() -> {
                setupPagination();
            });
        }
    }
}




