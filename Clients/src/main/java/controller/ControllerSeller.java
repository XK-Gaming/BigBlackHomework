package controller;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.Item;
import model.Items.ItemFactory;
import model.User.User;
import model.User.UserSession;
import network.AuctionClient;
import network.ClientNetworkExecutor;
import network.DataPacket;
import network.ServerListener;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;


/**
 * Controller cho màn hình người bán (Seller) tạo sản phẩm và mở phiên đấu giá.
 *
 * <p>Chức năng chính:
 * <ul>
 *   <li>Thu thập thông tin sản phẩm (tên, mô tả, giá khởi điểm, thời gian, loại item, fields phụ).</li>
 *   <li>(Tuỳ chọn) upload ảnh lên Cloudinary để lấy URL.</li>
 *   <li>Tạo {@link Item} thông qua {@link ItemFactory} rồi gửi lên server bằng command {@code CREATE_ITEM}.</li>
 * </ul>
 */
public class ControllerSeller implements ServerListener {
    /**
     * File ảnh được chọn từ máy người dùng.
     * NOTE: static -> dùng chung giữa các instance controller (có thể gây side-effect nếu mở nhiều màn).
     */
    public static File file = null;

    /** URL ảnh sau khi upload lên Cloudinary. */
    private static String fileName;

    /** Singleton network client dùng chung cho toàn app. */
    private AuctionClient client = AuctionClient.getInstance();

    /**
     * Precondition: {@code mouseEvent.getSource()} là {@link Node} hợp lệ.
     * Postcondition: Chuyển sang màn {@code View5.fxml}.
     * Method returns: nothing.
     */
    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }

    @FXML
    /**
     * Precondition: {@code LogOut} thuộc scene hiện tại.
     * Postcondition: Xoá session user và chuyển về {@code View1.fxml}.
     * Method returns: nothing.
     */
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) LogOut, "View1.fxml");
    }

    @FXML
    private Button LogOut;

    @FXML
    private Button j_ApplyItem;

    @FXML
    private Label error_Label;

    @FXML
    private ComboBox<String> j_ItemType;
    private String[] list = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};

    /**
     * Precondition: Các trường @FXML đã được inject.
     * Postcondition: Set combobox loại item, hiển thị tên user (từ session) và đăng ký listener.
     * NOTE: Nếu {@link UserSession#getLoggedInUser()} là null thì sẽ NullPointerException khi {@code p1.getName()}.
     * Method returns: nothing.
     */
    public void initialize() {
        j_ItemType.getItems().setAll(list);

        // Nếu muốn khi mở app lên nó chọn sẵn một cái (không bị trống)
        j_ItemType.setValue(list[0]);
        User p1 = UserSession.getLoggedInUser();
        j_LabelName.setText(p1.getName());
        // Đăng ký controller này làm người nghe tin nhắn từ Server
        client.setListener(this);
    }


    @FXML
    private Label j_LabelName;


    @FXML
    private TextField j_StartingPrice;

    @FXML
    private TextArea j_description;

    @FXML
    private TextField j_name;
    @FXML
    private DatePicker j_DateEnd; // Định dạng YYYY/mm/dd

    @FXML
    private DatePicker j_DateStart;

    @FXML
    private TextField j_TimeEnd; //Định dạng HH:mm:ss

    @FXML
    private TextField j_TimeStart;

    /**
     * Precondition:
     * - {@code j_date.getValue()} khác null
     * - {@code j_time.getText()} đúng format {@code HH:mm:ss}
     * Postcondition: Trả về {@link Instant} tương ứng theo múi giờ hệ thống.
     * NOTE: Sử dụng {@code ZoneId.systemDefault()} nên kết quả phụ thuộc timezone máy client.
     * Method returns: {@link Instant}.
     * NOTE: Nếu parse time sai format sẽ ném {@link java.time.format.DateTimeParseException}.
     */
    public Instant createInstant(DatePicker j_date, TextField j_time) {
        // 1. Lấy LocalDate từ DatePicker
        LocalDate date = j_date.getValue();
        // 2. Lấy String từ TextField (ví dụ: "15:30:45")
        String timeStr = j_time.getText();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime time = LocalTime.parse(timeStr, formatter);

        // 4. Kết hợp ngày + giờ + múi giờ hệ thống để tạo Instant
        // .atZone(ZoneId.systemDefault()) sẽ tự cộng/trừ chênh lệch múi giờ để ra chuẩn UTC
        Instant instant = LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        return instant;
    }

    @FXML
    private ImageView j_image;

    /**
     * Precondition: Các input bắt buộc đã được nhập; user đã login (có {@link UserSession}).
     * Postcondition:
     * - Nếu validate fail -> hiển thị lỗi.
     * - Nếu pass -> chạy luồng nền để:
     *   + (tuỳ chọn) upload ảnh
     *   + build {@link Item} từ {@link ItemFactory}
     *   + gửi {@code CREATE_ITEM} lên server
     * NOTE: Không chạy thao tác blocking trên UI thread; mọi thông báo UI được chuyển về bằng {@code Platform.runLater}.
     * Method returns: nothing.
     */
    public void handle_Items() {
        if (j_name.getText().isEmpty() || j_StartingPrice.getText().isEmpty() ||
                j_DateStart.getValue() == null || j_DateEnd.getValue() == null ||
                j_TimeStart.getText().isEmpty() || j_TimeEnd.getText().isEmpty()) {
            error_Label.setTextFill(Color.RED);
            error_Label.setText("Điền thông tin bắt buộc!");
            error_Label.setVisible(true);
            return;
        }

        User p1 = UserSession.getLoggedInUser();
        ClientNetworkExecutor.execute(() -> {
            try {
                if (file != null) {
                    fileName = uploadToCloudinary(file);
                }
                Instant start = createInstant(j_DateStart, j_TimeStart);
                Instant end = createInstant(j_DateEnd, j_TimeEnd);
                String itemType = j_ItemType.getValue();

                Map<String, String> extraFields = new HashMap<>();
                extraFields.put("brand", j_brand.getText());
                extraFields.put("model", j_model.getText());
                extraFields.put("manufacturer", j_manufacturer.getText());
                extraFields.put("year", j_year.getText());
                extraFields.put("artist", j_artist.getText());

                try {
                    double startingPrice = Double.parseDouble(j_StartingPrice.getText());
                    Item item = ItemFactory.createItem(
                            itemType,
                            j_name.getText(),
                            j_description.getText(),
                            startingPrice,
                            start, end,
                            p1.getUsername(),
                            extraFields,
                            fileName
                    );
                    client.sendCommand("CREATE_ITEM", item);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        error_Label.setTextFill(Color.RED);
                        error_Label.setText("Giá khởi điểm phải là số hợp lệ!");
                        error_Label.setVisible(true);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    e.printStackTrace();
                    error_Label.setTextFill(Color.RED);
                    error_Label.setText("Điền thông tin bắt buộc!");
                    error_Label.setVisible(true);
                });
            }

        });
    }

    @FXML
    private ImageView MyImgView;

    @FXML
    private Button j_img;

    @FXML
    void handle_SelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif")
        );

        // 'file' là biến static toàn cục bạn đã khai báo ở đầu class
        file = fileChooser.showOpenDialog(null);

        if (file != null) {
            // Hiển thị ảnh xem trước từ đường dẫn máy tính
            Image preview = new Image(file.toURI().toString());
            MyImgView.setImage(preview);

        }
    }

    void reset_pane() {
        j_paneArt.setVisible(false);
        j_paneElectronics.setVisible(false);
        j_paneVehicle.setVisible(false);
    }

    @FXML
    /**
     * Precondition: {@code j_ItemType.getValue()} không null.
     * Postcondition: Hiển thị pane nhập thông tin phụ tương ứng theo loại item; các pane còn lại bị ẩn.
     * Method returns: nothing.
     */
    void handle_Info(ActionEvent event) {
        String result = j_ItemType.getValue();
        if (result.equals("Mỹ thuật")) {
            reset_pane();
            j_paneArt.setVisible(true);
        }
        if (result.equals("Điện tử")) {
            reset_pane();
            j_paneElectronics.setVisible(true);
        }
        if (result.equals("Phương tiện giao thông")) {
            reset_pane();
            j_paneVehicle.setVisible(true);
        }
    }

    @FXML
    private TextField j_year;
    @FXML
    private Pane j_paneArt;

    @FXML
    private Pane j_paneElectronics;

    @FXML
    private Pane j_paneVehicle;
    @FXML
    private TextField j_manufacturer;

    @FXML
    private TextField j_model;

    @FXML
    private TextField j_artist;

    @FXML
    private TextField j_brand;

    /**
     * Precondition: {@code localFile} tồn tại và có quyền đọc.
     * Postcondition: Nếu upload thành công -> trả về URL HTTPS; nếu lỗi -> trả về null.
     * NOTE (BẢO MẬT): Không nên hardcode {@code api_key/api_secret} trong source code; nên đưa vào biến môi trường hoặc file cấu hình không commit.
     * Method returns: URL ảnh (String) hoặc null.
     */
    public String uploadToCloudinary(File localFile) {
        // Thông tin lấy từ Dashboard của bạn (dpkehgjjp)
        Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "dpkehgjjp",
                "api_key", "168924452148875",
                "api_secret", "JUjgUwOOFx0UegeGWa3VySf-wW4"
        ));

        try {
            Map uploadResult = cloudinary.uploader().upload(localFile, ObjectUtils.emptyMap());
            // Trả về link ảnh dạng https://res.cloudinary.com/...
            return (String) uploadResult.get("secure_url");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    /**
     * Precondition: Với {@code CREATE_ITEM_RESULT} thì payload là boolean.
     * Postcondition: Hiển thị message thành công/thất bại trên UI.
     * NOTE: UI update dùng {@code Platform.runLater}.
     * Method returns: nothing.
     * @throws ClassCastException NOTE: Nếu payload không phải boolean.
     */
    public void onServerResponse(DataPacket response) {
        String command = response.getCommand();

        if ("CREATE_ITEM_RESULT".equals(command)) {
            boolean isSuccess = (boolean) response.getPayload();

            Platform.runLater(() -> {
                if (isSuccess) {
                    error_Label.setTextFill(Color.BLUE);
                    error_Label.setText("Đăng bán sản phẩm thành công");
                } else {
                    error_Label.setTextFill(Color.RED);
                    error_Label.setText("Đăng bán sản phẩm thất bại");
                }
                error_Label.setVisible(true);
            });
        }
    }
}




