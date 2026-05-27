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

public class ControllerPayment implements ServerListener {
    private final AuctionClient client = AuctionClient.getInstance();
    private User p1;
    private Item item1;
    static model.auction.Auction this_Auction;

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

    public void On_apply(ActionEvent actionEvent) throws IOException {
        client.sendCommand(Command.BIDDER_PAY, Map.of("item", item1));
    }

    public void On_Return(ActionEvent actionEvent){
        SceneHelper.changeScene(j_return, "/fxml/BidderView.fxml");
        model.Items.ItemSession.cleanItemSession();
    }
    @FXML
    public void initialize() throws IOException {
        client.setListener(this);
        client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());

        // Nạp dữ liệu tài khoản
        p1 = model.User.UserSession.getLoggedInUser();
        // Kiểm tra xem có sẵn session sản phẩm không (Trường hợp đi từ Danh sách sản phẩm thông thường)
        item1 = model.Items.ItemSession.getLoggedInItem();

        if (item1 != null) {
            // Nếu đi từ màn hình chính (đã có ItemSession), kích hoạt kết nối luôn
            showSessionProductAndLoadingAuctionState();
        } else {
            // Nếu đi từ Lịch sử (ItemSession null), thiết lập giao diện chờ cơ bản trước
            if (p1 != null) {
                j_LabelName.setText(p1.getName());
                DecimalFormat df = new DecimalFormat("#,###");
                j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
            }
            j_status.setText("Đang tải cấu hình phòng...");
            j_status.setTextFill(Color.web("#bdc3c7"));
        }
    }

    private void showSessionProductAndLoadingAuctionState() {
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }
        if (item1 != null) {
            j_name.setText(item1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_description.setText(getCustomDescription(item1));
            renderImage();
            j_CurrentPrice.setText(df.format(item1.getCurrentHighestPrice()) + " VNĐ");
        }
    }
    private String getCustomDescription(Item item) {
        if (item == null || item.getDescription() == null) {
            return "Không có mô tả cho sản phẩm này.";
        }

        String rawDesc = item.getDescription().trim();
        if (rawDesc.isEmpty()) {
            return "Không có mô tả cho sản phẩm này.";
        }

        // Nếu không bọc trong ngoặc nhọn JSON, hiển thị như text thường
        if (!rawDesc.startsWith("{") || !rawDesc.endsWith("}")) {
            return rawDesc;
        }

        try {
            // Tạo Map để chứa dữ liệu sau khi bóc tách từ JSON
            Map<String, String> map = new HashMap<>();

            // Loại bỏ dấu ngoặc nhọn { và } ở hai đầu
            String cleanDesc = rawDesc.substring(1, rawDesc.length() - 1);

            // Tách các cặp thuộc tính bằng dấu phẩy
            String[] pairs = cleanDesc.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":");
                if (keyValue.length >= 2) {
                    // Lấy phần Key và loại bỏ toàn bộ dấu ngoặc kép '"' cũng như khoảng trắng
                    String key = keyValue[0].replace("\"", "").trim().toLowerCase();

                    // Lấy phần Value (gộp lại phòng trường hợp nội dung chứa dấu hai chấm ':')
                    StringBuilder valueBuilder = new StringBuilder();
                    for (int i = 1; i < keyValue.length; i++) {
                        if (i > 1) valueBuilder.append(":");
                        valueBuilder.append(keyValue[i]);
                    }
                    String value = valueBuilder.toString().replace("\"", "").trim();

                    map.put(key, value);
                }
            }

            // Lấy Loại sản phẩm chuẩn từ Enum có sẵn trong Model
            model.Items.ItemType type = item.getRawItemType();

            // Hiển thị thông tin chính xác theo từng danh mục sản phẩm
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

            // Phương án dự phòng cuối cùng nếu không nhận diện được Type
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String keyFormatted = entry.getKey().substring(0, 1).toUpperCase() + entry.getKey().substring(1);
                sb.append(keyFormatted).append(": ").append(entry.getValue()).append("\n");
            }
            return sb.toString().trim();

        } catch (Exception e) {
            System.err.println("Lỗi xử lý cú pháp JSON mô tả: " + e.getMessage());
            return rawDesc; // Trả về chuỗi gốc từ DB để giao diện không bị trống
        }
    }

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


    @Override
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        if (Command.GET_AUCTION_RESULT.equals(command)) {
            this_Auction = (model.auction.Auction) response.payload();

            Platform.runLater(() -> {
                if (this_Auction != null) {
                    if (this_Auction.getStatus().equals(AuctionStatus.PAID)) {
                        j_status.setText("Trạng thái: Đã thanh toán");
                        j_status.setVisible(true);
                    } else {
                        j_status.setText("Trạng thái: Chờ thanh toán");
                        j_status.setVisible(true);
                    }
                } else {
                    j_notified.setText("Phiên đấu giá không tồn tại hoặc đã bị xóa.");
                    j_notified.setVisible(true);
                    j_status.setText("KHÔNG TỒN TẠI");
                    j_status.setVisible(true);
                }
            });
    }
        if (Command.BIDDER_PAY_RESULT.equals(command)) {
            boolean result  = (boolean) response.payload();

            Platform.runLater(() -> {
                if (result) {
                        j_status.setText("Trạng thái: Đã thanh toán");
                        j_status.setVisible(true);
                        j_notified.setText("Thanh toán thành công.");
                        j_notified.setVisible(true);
                    } else {
                        j_status.setText("Trạng thái: Chờ thanh toán");
                        j_status.setVisible(true);
                        j_status.setText("Thanh toán thất bại");
                        j_status.setVisible(true);
                    }

            });
        }
}
}
