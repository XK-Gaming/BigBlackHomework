package controller;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import model.auction.AuctionStatus;
import network.AuctionClient;
import model.Items.Item;
import model.User.*;
import network.DataPacket;
import network.ServerListener;
import network.Command;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

// Màn thanh toán.
public class ControllerPayment implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private User p1;
    private Item item1;
    static model.auction.Auction this_Auction;

    @FXML
    private Button j_return_history;

    @FXML
    private Label j_CurrentPrice;

    @FXML
    private Label j_LabelName;

    @FXML
    private Button j_apply;

    @FXML
    private Label j_description;

    @FXML
    private ImageView j_image;

    @FXML
    private ImageView j_img;

    @FXML
    private Label j_name;

    @FXML
    private Label j_notified;

    @FXML
    private Button j_return;

    @FXML
    private Label j_status;

    @FXML
    private Label j_textSoDu;
    // Xử lý nút giao diện.
    public void On_Return_History(ActionEvent actionEvent) {
        client.removeListener(this);
        SceneHelper.changeScene(j_return_history, "/fxml/BidHistoryView.fxml");

    }
    // Xác nhận thanh toán.
    public void On_apply(ActionEvent actionEvent) throws IOException {
        if (item1 != null) {
            client.sendCommand(Command.BIDDER_PAY, Map.of("item", item1));
        } else {

            j_notified.setText("Lỗi: Không tìm thấy thông tin sản phẩm để thanh toán!");
            j_notified.setVisible(true);
            System.err.println("[Client] Không thể thanh toán vì item1 đang bị null.");
        }
    }
    // Xử lý nút giao diện.
    public void On_Return(ActionEvent actionEvent){
        client.removeListener(this);
        SceneHelper.changeScene(j_return, "/fxml/BidderView.fxml");
        model.Items.ItemSession.cleanItemSession();
    }
    // Nhận dữ liệu màn trước.
    public void initData(model.auction.BidHistoryDTO dto) {
        if (dto == null) return;

        System.out.println("[Payment Debug] Nhận dữ liệu từ Lịch sử cho Item ID: " + dto.getItemId());

        if (this.item1 == null) {
            this.item1 = new Item();
        }
        this.item1.setDatabaseId((int) dto.getItemId());
        this.item1.setName(dto.getItemName());

        this.item1.setCurrentHighestPrice(dto.getMyHighestBid() > 0 ? dto.getMyHighestBid() : dto.getCurrentHighestPrice());

        try {
            if (dto.getSellerId() != null && !dto.getSellerId().isBlank()) {
                this.item1.setSellerId(dto.getSellerId());
            }
        } catch (Exception ignored) {}

        model.Items.ItemSession.setLoggedInItem(this.item1);

        if (j_apply != null) j_apply.setDisable(true);

        showSessionProductAndLoadingAuctionState();

        try {
            client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());
        } catch (IOException e) {
            System.err.println("Lỗi gửi yêu cầu lấy phiên đấu giá: " + e.getMessage());
        }
    }

    // Khởi tạo màn hình.
    @FXML
    public void initialize() throws IOException {
        client.addListener(this);

        p1 = model.User.UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }

        item1 = model.Items.ItemSession.getLoggedInItem();

        if (item1 != null) {
            client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());
            showSessionProductAndLoadingAuctionState();

            j_apply.setDisable(true);
        } else {

            j_status.setText("Đang tải dữ liệu sản phẩm...");
            j_status.setTextFill(Color.web("#bdc3c7"));
            j_name.setText("Đang tải...");
            j_CurrentPrice.setText("0 VNĐ");
        }
    }
    // Hiện sản phẩm khi tải phiên.
    private void showSessionProductAndLoadingAuctionState() {
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }
        if (item1 != null) {

            String name = item1.getName();
            if (name == null || name.isBlank() || name.startsWith("Sản phẩm mã #")) {
                j_name.setText("Sản phẩm mã #" + item1.getDatabaseId());
            } else {
                j_name.setText(name);
            }

            DecimalFormat df = new DecimalFormat("#,###");
            j_description.setText(getCustomDescription(item1));
            renderImage();
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        }
    }
    // Ghép mô tả sản phẩm.
    private String getCustomDescription(Item item) {
        if (item == null || item.getDescription() == null) {
            return "Không có mô tả cho sản phẩm này.";
        }

        String rawDesc = item.getDescription().trim();
        if (rawDesc.isEmpty()) {
            return "Không có mô tả cho sản phẩm này.";
        }

        if (!rawDesc.startsWith("{") || !rawDesc.endsWith("}")) {
            return rawDesc;
        }

        try {

            Map<String, String> map = new HashMap<>();

            String cleanDesc = rawDesc.substring(1, rawDesc.length() - 1);

            String[] pairs = cleanDesc.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length >= 2) {

                    String key = keyValue[0].replace("\"", "").trim().toLowerCase();

                    StringBuilder valueBuilder = new StringBuilder();
                    for (int i = 1; i < keyValue.length; i++) {
                        if (i > 1) valueBuilder.append(":");
                        valueBuilder.append(keyValue[i]);
                    }
                    String value = valueBuilder.toString().replace("\"", "").trim();

                    map.put(key, value);
                }
            }

            model.Items.ItemType type = item.getRawItemType();

            if (type != null) {
                switch (type) {
                    case ART:
                        String artDesc = map.getOrDefault("description", "Không có mô tả");
                        String artist = map.getOrDefault("artist", "Không rõ");
                        return "Mô tả: " + artDesc + "\nHọa sĩ: " + artist;

                    case ELECTRONICS:
                        String brand = map.getOrDefault("brand", "Không rõ");
                        String model = map.getOrDefault("model", "Không rõ");
                        return "Thương hiệu: " + brand + "\nModel: " + model;

                    case VEHICLE:
                        String year = map.getOrDefault("year", "Không rõ");
                        String manufacturer = map.getOrDefault("manufacturer", "Không rõ");
                        return "Năm sản xuất: " + year + "\nNhà sản xuất: " + manufacturer;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String keyFormatted = entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1);
                sb.append(keyFormatted).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            System.err.println("Lỗi xử lý cú pháp JSON mô tả: " + e.getMessage());
            return rawDesc;
        }
    }
    // Hiển thị ảnh sản phẩm.
    private void renderImage() {
            if (item1 != null && item1.getImg() != null && !item1.getImg().isEmpty()) {
                if (item1.getImg().startsWith("http")) {
                    j_img.setImage(new Image(item1.getImg()));
                } else {
                    String imgPath = "/controller/img/" + item1.getImg();
                    java.net.URL imgUrl = getClass().getResource(imgPath);
                    if (imgUrl != null) {
                        j_img.setImage(new Image(imgUrl.toExternalForm()));
                    }
                }
            }
        }

    // Xử lý phản hồi server.
    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        // Nhận phiên đấu giá.
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            this_Auction = (model.auction.Auction) response.payload();

            Platform.runLater(() -> {
                if (this_Auction != null) {

                    if (this_Auction.getItem() != null) {
                        this.item1 = this_Auction.getItem();
                        model.Items.ItemSession.setLoggedInItem(this.item1);
                    }
                    if (this_Auction.getStatus().equals(AuctionStatus.PAID)) {
                        j_status.setText("Trạng thái: Đã thanh toán");
                        j_status.setTextFill(Color.GREEN);
                        j_status.setVisible(true);

                        j_apply.setDisable(true);
                        j_notified.setText("Sản phẩm này đã được thanh toán hoàn tất.");
                        j_notified.setVisible(true);
                    } else {
                        j_status.setText("Trạng thái: Chờ thanh toán");
                        j_status.setVisible(true);

                        j_apply.setDisable(false);
                    }
                } else {
                    j_notified.setText("Phiên đấu giá không tồn tại hoặc đã bị xóa.");
                    j_notified.setVisible(true);
                    j_status.setText("KHÔNG TỒN TẠI");
                    j_status.setVisible(true);
                    j_apply.setDisable(true);
                }
            });
        }

        // Nhận kết quả thanh toán.
        if (Command.BIDDER_PAY_RESULT.equals(command)) {
            Map<String, Object> resMap = (Map<String, Object>) response.payload();
            boolean result = resMap != null && (boolean) resMap.getOrDefault("success", false);

            Platform.runLater(() -> {
                if (result) {
                    j_status.setText("Trạng thái: Đã thanh toán");
                    j_status.setTextFill(Color.GREEN);
                    j_status.setVisible(true);
                    j_notified.setText("Thanh toán thành công!");
                    j_notified.setVisible(true);

                    j_apply.setDisable(true);
                } else {
                    j_status.setText("Trạng thái: Thanh toán thất bại");
                    j_status.setTextFill(Color.RED);
                    j_status.setVisible(true);
                    String message = null;
                    try { message = (String) resMap.getOrDefault("message", null); } catch (Exception ignored) {}
                    if (message != null && !message.isBlank()) {
                        j_notified.setText(message);
                    } else {
                        j_notified.setText("Thanh toán không thành công. Vui lòng kiểm tra lại số dư!");
                    }
                    j_notified.setVisible(true);

                    j_apply.setDisable(false);
                }
            });
        }
    }
}
