package controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.Item;
import model.Items.ItemFactory;
import model.User.User;
import model.User.UserSession;
import network.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

public class ControllerSeller implements ServerListener {

    // Tối ưu 1: Bỏ 'static', chuyển thành Instance Variable để an toàn bộ nhớ
    private File selectedFile = null;
    private String uploadedImageUrl = null;
    private Cloudinary cloudinary;

    private final AuctionClient client = AuctionClient.getInstance();
    private final String[] categoryList = new String[]{"Mỹ thuật", "Điện tử", "Phương tiện giao thông"};

    // Tối ưu 2: Dùng Map để quản lý việc ẩn hiện Pane gọn gàng
    private final Map<String, HBox> categoryPaneMap = new HashMap<>();

    @FXML private Button j_ApplyItem;
    @FXML private Label error_Label;
    @FXML private ComboBox<String> j_ItemType;
    @FXML private Label j_LabelName;
    @FXML private TextField j_StartingPrice;
    @FXML private TextArea j_description;
    @FXML private TextField j_name;
    @FXML private DatePicker j_DateEnd;
    @FXML private DatePicker j_DateStart;
    @FXML private TextField j_TimeEnd;
    @FXML private TextField j_TimeStart;
    @FXML private ImageView MyImgView;

    // Sửa kiểu dữ liệu từ Pane -> HBox cho khớp chuẩn với FXML
    @FXML private HBox j_paneArt;
    @FXML private HBox j_paneElectronics;
    @FXML private HBox j_paneVehicle;

    @FXML private TextField j_year;
    @FXML private TextField j_manufacturer;
    @FXML private TextField j_model;
    @FXML private TextField j_artist;
    @FXML private TextField j_brand;

    public void initialize() {
        initCloudinary();

        // Ánh xạ phục vụ việc ẩn/hiện động
        categoryPaneMap.put("Mỹ thuật", j_paneArt);
        categoryPaneMap.put("Điện tử", j_paneElectronics);
        categoryPaneMap.put("Phương tiện giao thông", j_paneVehicle);

        j_ItemType.getItems().setAll(categoryList);
        j_ItemType.setValue(categoryList[0]);

        // Đồng bộ hiển thị Form mở rộng ngay từ đầu
        handle_Info(null);

        User user = UserSession.getLoggedInUser();
        if (user != null) {
            j_LabelName.setText(user.getName());
            DecimalFormat df = new DecimalFormat("#,###");
            j_textSoDu.setText(df.format(user.getBalance()) + " VNĐ");
        }

        client.setListener(this);

        // Chặn ngày hợp lệ (Giữ nguyên logic tốt của bạn)
        j_DateStart.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });

        j_DateEnd.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                LocalDate minDate = (j_DateStart.getValue() != null) ? j_DateStart.getValue() : LocalDate.now();
                setDisable(empty || date.isBefore(minDate));
            }
        });

        j_DateStart.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && j_DateEnd.getValue() != null && j_DateEnd.getValue().isBefore(newVal)) {
                j_DateEnd.setValue(newVal);
            }
        });
    }

    // Tối ưu Bảo mật: Đọc cấu hình từ file bên ngoài thay vì Hardcode
    private void initCloudinary() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("client.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", prop.getProperty("cloudinary.cloud_name"),
                        "api_key", prop.getProperty("cloudinary.api_key"),
                        "api_secret", prop.getProperty("cloudinary.api_secret")
                ));
            } else {
                // Fallback nếu thiếu file cấu hình (Cảnh báo lập trình viên)
                System.err.println("Warning: config.properties not found. Using hardcoded credentials temporary.");
                cloudinary = new Cloudinary(ObjectUtils.asMap(
                        "cloud_name", "dpkehgjjp", "api_key", "168924452148875", "api_secret", "JUjgUwOOFx0UegeGWa3VySf-wW4"
                ));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public Instant createInstant(DatePicker j_date, TextField j_time) throws DateTimeParseException {
        LocalDate date = j_date.getValue();
        String timeStr = j_time.getText().trim();

        // Chuẩn hóa chuỗi thời gian nếu người dùng lỡ nhập thiếu giây (ví dụ: "15:30" -> "15:30:00")
        if (timeStr.matches("^\\d{1,2}:\\d{2}$")) {
            timeStr += ":00";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime time = LocalTime.parse(timeStr, formatter);

        return LocalDateTime.of(date, time)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    @FXML
    void handle_Items() {
        // --- BẮT ĐẦU PHẦN KIỂM TRA THÔNG TIN BẮT BUỘC CHI TIẾT ---
        if (j_name.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Tên sản phẩm (*)");
            j_name.requestFocus();
            return;
        }

        if (j_DateStart.getValue() == null || j_TimeStart.getText().trim().isEmpty()) {
            showError("Vui lòng chọn đầy đủ Ngày và Giờ bắt đầu (*)");
            if (j_DateStart.getValue() == null) j_DateStart.requestFocus();
            else j_TimeStart.requestFocus();
            return;
        }

        if (j_DateEnd.getValue() == null || j_TimeEnd.getText().trim().isEmpty()) {
            showError("Vui lòng chọn đầy đủ Ngày và Giờ kết thúc (*)");
            if (j_DateEnd.getValue() == null) j_DateEnd.requestFocus();
            else j_TimeEnd.requestFocus();
            return;
        }

        if (j_StartingPrice.getText().trim().isEmpty()) {
            showError("Vui lòng nhập Giá khởi điểm sản phẩm (*)");
            j_StartingPrice.requestFocus();
            return;
        }
        // --- KẾT THÚC KIỂM TRA THÔNG TIN BẮT BUỘC ---

        j_ApplyItem.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xử lý dữ liệu và tải ảnh lên...");
        error_Label.setVisible(true);

        User user = UserSession.getLoggedInUser();

        ClientNetworkExecutor.execute(() -> {
            try {
                // Tải ảnh lên Cloudinary
                if (selectedFile != null) {
                    uploadedImageUrl = uploadToCloudinary(selectedFile);
                    if (uploadedImageUrl == null) {
                        throw new Exception("Tải hình ảnh lên Cloudinary thất bại!");
                    }
                }

                // Xử lý và kiểm tra Logic Thời gian
                Instant start, end;
                try {
                    start = createInstant(j_DateStart, j_TimeStart);
                    end = createInstant(j_DateEnd, j_TimeEnd);

                    if (start.isBefore(Instant.now())) {
                        throw new IllegalArgumentException("Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại!");
                    }
                    if (end.isBefore(start)) {
                        throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu!");
                    }
                } catch (DateTimeParseException e) {
                    showErrorInUIThread("Định dạng giờ không hợp lệ (HH:mm:ss)!");
                    return;
                } catch (IllegalArgumentException e) {
                    showErrorInUIThread(e.getMessage());
                    return;
                }

                // Kiểm tra Logic giá tiền
                double startingPrice;
                try {
                    startingPrice = Double.parseDouble(j_StartingPrice.getText());
                    if (startingPrice <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showErrorInUIThread("Giá khởi điểm phải là số dương hợp lệ!");
                    return;
                }

                // Đóng gói extra fields dựa trên loại mặt hàng chọn
                String itemType = j_ItemType.getValue();
                Map<String, String> extraFields = new HashMap<>();
                extraFields.put("brand", j_brand.getText());
                extraFields.put("model", j_model.getText());
                extraFields.put("manufacturer", j_manufacturer.getText());
                extraFields.put("year", j_year.getText());
                extraFields.put("artist", j_artist.getText());

                Item item = ItemFactory.createItem(
                        itemType, j_name.getText(), j_description.getText(), startingPrice,
                        start, end, user != null ? user.getUsername() : "Unknown",
                        extraFields, uploadedImageUrl
                );

                client.sendCommand(Command.CREATE_ITEM, item);

            } catch (Exception e) {
                e.printStackTrace();
                showErrorInUIThread("Có lỗi xảy ra trong quá trình đăng sản phẩm!");
            }
        });
    }

    public String uploadToCloudinary(File localFile) {
        try {
            Map uploadResult = cloudinary.uploader().upload(localFile, ObjectUtils.emptyMap());
            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @FXML
    void handle_SelectImage(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        File file = fileChooser.showOpenDialog(((Node) event.getSource()).getScene().getWindow());
        if (file != null) {
            this.selectedFile = file; // Lưu vào instance variable
            MyImgView.setImage(new Image(file.toURI().toString()));
        }
    }

    // Tối ưu 3: Hàm xử lý thông tin ẩn hiện cực kỳ Clean & mở rộng dễ dàng
    @FXML
    void handle_Info(ActionEvent event) {
        String selectedCategory = j_ItemType.getValue();
        categoryPaneMap.forEach((category, pane) -> {
            if (pane != null) {
                pane.setVisible(category.equals(selectedCategory));
            }
        });
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/LoginView.fxml");
    }

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/AccountInfoView.fxml");
    }

    private void showError(String message) {
        error_Label.setTextFill(Color.RED);
        error_Label.setText(message);
        error_Label.setVisible(true);
    }

    private void showErrorInUIThread(String message) {
        Platform.runLater(() -> {
            showError(message);
            j_ApplyItem.setDisable(false);
        });
    }

    @Override
    public void onServerResponse(DataPacket response) {
        if (Command.CREATE_ITEM_RESULT.equals(response.command())) {
            boolean isSuccess = (boolean) response.payload();
            Platform.runLater(() -> {
                j_ApplyItem.setDisable(false); // Mở lại nút bấm sau khi nhận phản hồi
                if (isSuccess) {
                    error_Label.setTextFill(Color.GREEN); // Chuyển xanh lục chuẩn thành công thay vì xanh lam
                    error_Label.setText("Đăng bán sản phẩm thành công!");
                } else {
                    showError("Đăng bán sản phẩm thất bại từ phía máy chủ.");
                }
            });
        }
        if (Command.NOTIFICATION.equals(response.command())) {
            Platform.runLater(() -> {
                ControllerNotificationSeller.handleIncomingToastNotificationSeller(response.payload(), j_textSoDu);
            });
        }
    }
    @FXML
    private Label j_textSoDu;

    @FXML
    void On_ProductList(ActionEvent event) {
        SceneHelper.changeScene((Node) event.getSource(), "/fxml/ProductListView.fxml");

}
}