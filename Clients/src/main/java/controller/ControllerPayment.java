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

    public void On_Return_History(ActionEvent actionEvent) {
        SceneHelper.changeScene(j_return_history, "/fxml/BidHistoryView.fxml");

        // Lưu ý: Không gọi cleanItemSession() ở đây nếu bạn muốn giữ lại trạng thái item vừa xem
    }

    public void On_apply(ActionEvent actionEvent) throws IOException {
        if (item1 != null) {
            client.sendCommand(Command.BIDDER_PAY, Map.of("item", item1));
        } else {
            // Thông báo cho người dùng biết (nếu có Label thông báo)
            j_notified.setText("Lỗi: Không tìm thấy thông tin sản phẩm để thanh toán!");
            j_notified.setVisible(true);
            System.err.println("[Client] Không thể thanh toán vì item1 đang bị null.");
        }
    }

    public void On_Return(ActionEvent actionEvent){
        SceneHelper.changeScene(j_return, "/fxml/BidderView.fxml");
        model.Items.ItemSession.cleanItemSession();
    }
    // --- THÊM HÀM NÀY ĐỂ NHẬN DỮ LIỆU TỪ MÀN HÌNH LỊCH SỬ ---
    public void initData(model.auction.BidHistoryDTO dto) {
        if (dto == null) return;

        System.out.println("[Payment Debug] Nhận dữ liệu từ Lịch sử cho Item ID: " + dto.getItemId());

        // 1. Khởi tạo một đối tượng Item tạm thời từ dữ liệu DTO nhận được
        if (this.item1 == null) {
            this.item1 = new Item();
        }
        this.item1.setDatabaseId((int) dto.getItemId());
        this.item1.setName(dto.getItemName());
        // Giá cần thanh toán chính là giá thắng cuộc (Mức đặt cao nhất của bạn)
        this.item1.setCurrentHighestPrice(dto.getMyHighestBid() > 0 ? dto.getMyHighestBid() : dto.getCurrentHighestPrice());

        // 2. Lưu vào Session để các hàm bổ trợ khác (như On_apply, getCustomDescription) chạy không bị lỗi
        model.Items.ItemSession.setLoggedInItem(this.item1);

        // 3. Cập nhật giao diện ngay lập tức
        showSessionProductAndLoadingAuctionState();

        // 4. Gửi lệnh lên server để đồng bộ trạng thái thanh toán thực tế của phiên đấu giá
        try {
            client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());
        } catch (IOException e) {
            System.err.println("Lỗi gửi yêu cầu lấy phiên đấu giá: " + e.getMessage());
        }
    }

    @FXML
    public void initialize() throws IOException {
        client.setListener(this);

        // Nạp thông tin người dùng đăng nhập
        p1 = model.User.UserSession.getLoggedInUser();
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }

        // Kiểm tra xem có Item từ màn hình khác (như sảnh chính) đi vào không
        item1 = model.Items.ItemSession.getLoggedInItem();

        if (item1 != null) {
            client.sendCommand(Command.GET_AUCTION, item1.getDatabaseId());
            showSessionProductAndLoadingAuctionState();
        } else {
            // Nếu chưa có item1 (tức là đi từ Lịch Sử, đang chờ hàm initData được gọi từ màn hình trước)
            j_status.setText("Đang tải dữ liệu sản phẩm...");
            j_status.setTextFill(Color.web("#bdc3c7"));
            j_name.setText("Đang tải...");
            j_CurrentPrice.setText("0 VNĐ");
        }
    }

    private void showSessionProductAndLoadingAuctionState() {
        if (p1 != null) {
            j_LabelName.setText(p1.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(p1.getBalance()) + " VNĐ");
        }
        if (item1 != null) {
            // Hiển thị tên sản phẩm an toàn
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

        // 1. Trường hợp: Lấy thông tin phiên đấu giá để đồng bộ trạng thái khi vừa vào màn hình
        if (Command.GET_AUCTION_RESULT.equals(command)) {
            this_Auction = (model.auction.Auction) response.payload();

            Platform.runLater(() -> {
                if (this_Auction != null) {
                    if (this_Auction.getStatus().equals(AuctionStatus.PAID)) {
                        j_status.setText("Trạng thái: Đã thanh toán");
                        j_status.setTextFill(Color.GREEN); // (Tùy chọn) Đổi chữ sang màu xanh cho trực quan
                        j_status.setVisible(true);

                        // THÊM: Disable nút thanh toán và thông báo nếu đã thanh toán trước đó
                        j_apply.setDisable(true);
                        j_notified.setText("Sản phẩm này đã được thanh toán hoàn tất.");
                        j_notified.setVisible(true);
                    } else {
                        j_status.setText("Trạng thái: Chờ thanh toán");
                        j_status.setVisible(true);

                        // Đảm bảo nút được mở lại nếu chưa thanh toán
                        j_apply.setDisable(false);
                    }
                } else {
                    j_notified.setText("Phiên đấu giá không tồn tại hoặc đã bị xóa.");
                    j_notified.setVisible(true);
                    j_status.setText("KHÔNG TỒN TẠI");
                    j_status.setVisible(true);
                    j_apply.setDisable(true); // Không tồn tại phiên thì không cho bấm
                }
            });
        }

        // 2. Trường hợp: Kết quả sau khi người dùng bấm nút Thanh toán (On_apply)
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

                    // THÊM: Disable nút ngay lập tức sau khi thanh toán thành công thành công
                    j_apply.setDisable(true);
                } else {
                    j_status.setText("Trạng thái: Thanh toán thất bại");
                    j_status.setTextFill(Color.RED); // (Tùy chọn) Đổi chữ sang màu đỏ khi lỗi
                    j_status.setVisible(true);
                    j_notified.setText("Thanh toán không thành công. Vui lòng kiểm tra lại số dư!");
                    j_notified.setVisible(true);

                    // Thất bại thì vẫn cho phép bấm thử lại
                    j_apply.setDisable(false);
                }
            });
        }
    }
}
