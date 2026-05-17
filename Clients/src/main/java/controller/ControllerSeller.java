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
import network.*;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;


public class ControllerSeller implements ServerListener {
    public static File file = null;
    private static String fileName;
    private AuctionClient client = AuctionClient.getInstance();

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "/fxml/View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }

    @FXML
    void On_LogOut(ActionEvent event) {
        UserSession.cleanUserSession();
        SceneHelper.changeScene((Node) LogOut, "/fxml/LoginView.fxml");
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

    public void initialize() {
        j_ItemType.getItems().setAll(list);
        j_ItemType.setValue(list[0]);

        User p1 = UserSession.getLoggedInUser();
        j_LabelName.setText(p1.getName());

        client.setListener(this);

        // chặn ngày không hợp lệ
        // 1. j_DateStart không được chọn ngày trước ngày hôm nay
        j_DateStart.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date.isBefore(LocalDate.now()));
            }
        });

        // 2. j_DateEnd không được chọn ngày trước j_DateStart (hoặc trước ngày hôm nay)
        j_DateEnd.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);

                // Nếu j_DateStart đã chọn ngày, thì j_DateEnd phải từ ngày đó trở đi
                // Nếu j_DateStart chưa chọn, thì mặc định j_DateEnd không được trước ngày hôm nay
                LocalDate minDate = (j_DateStart.getValue() != null) ? j_DateStart.getValue() : LocalDate.now();

                setDisable(empty || date.isBefore(minDate));
            }
        });

        // 3. Tự động cập nhật lại j_DateEnd nếu người dùng thay đổi j_DateStart
        j_DateStart.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null && j_DateEnd.getValue() != null && j_DateEnd.getValue().isBefore(newValue)) {
                // Nếu ngày kết thúc cũ lỡ nhỏ hơn ngày bắt đầu mới chọn -> tự động xóa hoặc reset
                j_DateEnd.setValue(newValue);
            }
        });
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

    public void handle_Items() {
        if (j_name.getText().isEmpty() || j_StartingPrice.getText().isEmpty() ||
                j_DateStart.getValue() == null || j_DateEnd.getValue() == null ||
                j_TimeStart.getText().isEmpty() || j_TimeEnd.getText().isEmpty()) {
            error_Label.setTextFill(Color.RED);
            error_Label.setText("Điền thông tin bắt buộc!");
            error_Label.setVisible(true);
            return;
        }
        // Vô hiệu hóa nút để tránh người dùng click nhiều lần (Spam)
        j_ApplyItem.setDisable(true);
        error_Label.setTextFill(Color.BLACK);
        error_Label.setText("Đang xử lý dữ liệu và tải ảnh lên...");
        error_Label.setVisible(true);

        User p1 = UserSession.getLoggedInUser();

        ClientNetworkExecutor.execute(() -> {
            try {
                if (file != null) {
                    fileName = uploadToCloudinary(file);
                }
                //  Bắt lỗi định dạng thời gian nhập vào
                Instant start, end;
                try {
                    start = createInstant(j_DateStart, j_TimeStart);
                    end = createInstant(j_DateEnd, j_TimeEnd);

                    // Lấy thời gian hiện tại của hệ thống (UTC) để so sánh
                    Instant now = Instant.now();

                    // Kiểm tra 1: Thời gian bắt đầu không được ở quá khứ
                    if (start.isBefore(now)) {
                        throw new IllegalArgumentException("Thời gian bắt đầu không được nhỏ hơn thời gian hiện tại!");
                    }

                    // Kiểm tra 2: Thời gian kết thúc phải sau thời gian bắt đầu
                    if (end.isBefore(start)) {
                        throw new IllegalArgumentException("Thời gian kết thúc phải sau thời gian bắt đầu!");
                    }
                } catch (IllegalArgumentException e) {
                    // Bắt riêng lỗi logic do mình tự throw (quá khứ hoặc sai thứ tự)
                    Platform.runLater(() -> {
                        error_Label.setTextFill(Color.RED);
                        error_Label.setText(e.getMessage()); // Hiển thị đúng câu báo lỗi tùy trường hợp
                        j_ApplyItem.setDisable(false);
                    });
                    return;
                } catch (Exception e) {
                    // Bắt lỗi sai định dạng parse giờ (HH:mm:ss) hoặc DatePicker trống
                    Platform.runLater(() -> {
                        error_Label.setTextFill(Color.RED);
                        error_Label.setText("Định dạng giờ không hợp lệ (HH:mm:ss) hoặc ngày nhập bị lỗi!");
                        j_ApplyItem.setDisable(false);
                    });
                    return;
                }
                // 4. Bắt lỗi định dạng giá tiền
                double startingPrice;
                try {
                    startingPrice = Double.parseDouble(j_StartingPrice.getText());
                    if (startingPrice <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    Platform.runLater(() -> {
                        error_Label.setTextFill(Color.RED);
                        error_Label.setText("Giá khởi điểm phải là số dương hợp lệ!");
                        j_ApplyItem.setDisable(false);
                    });
                    return;
                }
                // 5. Chuẩn bị dữ liệu mở rộng theo loại mặt hàng
                String itemType = j_ItemType.getValue();
                Map<String, String> extraFields = new HashMap<>();
                extraFields.put("brand", j_brand.getText());
                extraFields.put("model", j_model.getText());
                extraFields.put("manufacturer", j_manufacturer.getText());
                extraFields.put("year", j_year.getText());
                extraFields.put("artist", j_artist.getText());
                // 6. Tạo đối tượng và gửi lên Server
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
                client.sendCommand(Command.CREATE_ITEM, item);
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    error_Label.setTextFill(Color.RED);
                    error_Label.setText("Có lỗi xảy ra trong quá trình đăng sản phẩm!");
                    j_ApplyItem.setDisable(false);
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
    public void onServerResponse(DataPacket response) {
        Command command = response.command();

        if (Command.CREATE_ITEM_RESULT.equals(command)) {
            boolean isSuccess = (boolean) response.payload();

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




