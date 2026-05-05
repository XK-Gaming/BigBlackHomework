package service;

import dao.DAOItems;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import model.Items.*;
import model.User.User;
import model.User.UserSession;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ControllerSeller {
    public static File file = null;
    private static String fileName;

    public void On_MouseClickImg(javafx.scene.input.MouseEvent mouseEvent) {
        SceneHelper.changeScene((Node) mouseEvent.getSource(), "View5.fxml");
        // Mục đích (Node) event.getSource() là để lấy Node hiện tại đó
    }

    @FXML
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

    public void initialize() {
        j_ItemType.getItems().setAll(list);

        // Nếu muốn khi mở app lên nó chọn sẵn một cái (không bị trống)
        j_ItemType.setValue(list[0]);
        User p1 = UserSession.getLoggedInUser();
        j_LabelName.setText(p1.getName());

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
        User p1 = UserSession.getLoggedInUser();
        try {
            // 1. Chuẩn bị dữ liệu chung
            Instant start = createInstant(j_DateStart, j_TimeStart);
            Instant end = createInstant(j_DateEnd, j_TimeEnd);
            String itemType = j_ItemType.getValue();

// 2. Thu thập các field đặc thù vào một Map để truyền cho Factory
            Map<String, String> extraFields = new HashMap<>();
            extraFields.put("brand", j_brand.getText());
            extraFields.put("model", j_model.getText());
            extraFields.put("manufacturer", j_manufacturer.getText());
            extraFields.put("year", j_year.getText());
            extraFields.put("artist", j_artist.getText());

            try {
                // 3. Sử dụng Factory để tạo object
                Item item = ItemFactory.createItem(
                        itemType,
                        j_name.getText(),
                        j_description.getText(),
                        Double.parseDouble(j_StartingPrice.getText()),
                        start, end,
                        p1.getUsername(),
                        extraFields,
                        fileName
                );

                // 4. Thực hiện Insert (Chỉ cần gọi một lần duy nhất cho mọi loại Item)
                DAOItems.getInstance().Insert(item);

            } catch (IllegalArgumentException e) {
                // Xử lý lỗi nếu type không khớp
                System.err.println(e.getMessage());
            }
            error_Label.setText("Đăng bán sản phẩm thành công");
            error_Label.setVisible(true);
        } catch (Exception e) {
            error_Label.setTextFill(Color.RED);
            error_Label.setText("Điền thông tin bắt buộc!");
            error_Label.setVisible(true);
        }

    }

    @FXML
    private ImageView MyImgView;

    @FXML
    private Button j_img;

    @FXML
    void handle_SelectImage(ActionEvent event) throws IOException {
        FileChooser fileChooser = new FileChooser();

        // Chỉ hiện các định dạng ảnh
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif")
        );

        // Mở cửa sổ chọn file
        file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                fileName = System.currentTimeMillis() + "_" + file.getName();

                // Trong môi trường development, ta vẫn cần lưu file vật lý để dùng
                // Nhưng ta sẽ lưu vào folder resources để Maven có thể đóng gói được
                String resourcePath = "src/main/resources/du_an_lon/img/" + fileName;
                File dest = new File(resourcePath);

                if (!dest.getParentFile().exists()) {
                    dest.getParentFile().mkdirs();
                }

                Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Hiển thị lên UI dùng URI từ file vừa lưu
                MyImgView.setImage(new Image(dest.toURI().toString()));

            } catch (IOException e) {
                e.printStackTrace();
            }
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


}




